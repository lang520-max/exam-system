package com.exam;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.exam.common.exception.BusinessException;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.FaceEnrollForm;
import com.exam.dto.request.FaceVerifyForm;
import com.exam.dto.response.FaceStatusVO;
import com.exam.dto.response.FaceVerifyResultVO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.FaceVerifyLog;
import com.exam.entity.Question;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.FaceVerifyLogMapper;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import com.exam.service.FaceRecognitionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.exam.common.constant.QuestionType.SINGLE;
import static org.assertj.core.api.Assertions.*;

/**
 * 人脸核验门禁的集成测试。
 *
 * <p>假 CompareFace 服务用 JDK 自带的 {@link HttpServer}（与 AiGradingServiceTest 同一套思路），
 * 因此这个测试**不花钱、不联网、不需要 AK**，却能覆盖真实链路：注册录入 → 开考核验 →
 * 门禁判定 → 留痕 → 教师放行。</p>
 *
 * <p>注意端口是写死的：{@code @SpringBootTest(properties=...)} 里必须能在注解上写死地址，
 * 而 Spring 容器在 {@code @BeforeAll} 之前就建好了，所以假服务在静态初始化块里启动。</p>
 */
@SpringBootTest(properties = {
        "app.face.enabled=true",
        "app.face.endpoint=http://127.0.0.1:18973",
        "app.face.access-key-id=test-ak",
        "app.face.access-key-secret=test-sk",
        "app.face.threshold=75",
        "app.face.max-retries=3",
        "app.face.pass-window-seconds=300"
})
@DisplayName("人脸核验：开考门禁")
class FaceVerifyGateTest extends BaseIntegrationTest {

    private static final int FAKE_PORT = 18973;
    private static HttpServer fake;
    private static final AtomicInteger compareCalls = new AtomicInteger();
    /** 下一次调用的响应体与状态码（由各用例设置） */
    private static volatile String nextBody = "{\"Data\":{\"Confidence\":90.0}}";
    private static volatile int nextStatus = 200;

    static {
        try {
            fake = HttpServer.create(new InetSocketAddress("127.0.0.1", FAKE_PORT), 0);
            fake.createContext("/", exchange -> {
                compareCalls.incrementAndGet();
                byte[] body = nextBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(nextStatus, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            fake.start();
        } catch (Exception e) {
            throw new IllegalStateException("假人脸服务启动失败（端口 " + FAKE_PORT + " 被占用？）", e);
        }
    }

    @AfterAll
    static void stopFake() {
        if (fake != null) {
            fake.stop(0);
        }
    }

    @Autowired
    private FaceRecognitionService faceService;
    @Autowired
    private FaceVerifyLogMapper logMapper;
    @Autowired
    private ExamService examService;
    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamRecordMapper recordMapper;

    private static final String IMAGE = "data:image/jpeg;base64,/9j/AAAATESTIMAGE";

    /** 造一份已发布的考试（沿用全流程集成测试的组卷方式） */
    private Exam publishedExam() {
        Question q = createQuestion(bank.getId(), SINGLE, 1, "人脸测试题", "A", 10);
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("人脸门禁测试卷_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(6);
        ExamGenerateDTO.RuleItem rule = new ExamGenerateDTO.RuleItem();
        rule.setType(SINGLE);
        rule.setCount(1);
        rule.setScore(10);
        form.setRules(List.of(rule));
        Exam exam = examRuleService.generateExam(form, teacher.getId());
        exam.setStatus(1);
        examMapper.updateById(exam);
        return exam;
    }

    private void enroll() {
        FaceEnrollForm form = new FaceEnrollForm();
        form.setImageBase64(IMAGE);
        form.setLiveness(true);
        faceService.enroll(student.getId(), form, "127.0.0.1");
    }

    private FaceVerifyResultVO verify(Long examId, boolean liveness) {
        FaceVerifyForm form = new FaceVerifyForm();
        form.setExamId(examId);
        form.setImageBase64(IMAGE);
        form.setLiveness(liveness);
        return faceService.verify(student.getId(), form, "127.0.0.1");
    }

    @Test
    @DisplayName("画面里没人脸（厂商返回 InvalidImage.*）→ 判【失败】扣额度，而不是「服务不可用」")
    void imageProblemCountsAsStudentFailure() {
        Exam exam = publishedExam();
        enroll();
        // 真机联调实测：没对准镜头时厂商就是这个错误码
        nextStatus = 200;
        nextBody = "{\"Code\":\"InvalidImage.FaceNotDetected\",\"Message\":\"未检测到人脸\"}";
        FaceVerifyResultVO r = verify(exam.getId(), true);
        assertThat(r.getResult()).isEqualTo("FAIL");
        assertThat(r.getMessage()).contains("照片不合格");
        assertThat(r.getRetriesLeft()).isEqualTo(2);
        assertThat(r.isNeedTeacherOverride()).isFalse();
        assertThat(logMapper.countFailedAttempts(student.getId(), exam.getId())).isEqualTo(1);
        // 而且这时候【不该】要求教师放行 —— 学生自己能重拍
        assertThat(logMapper.selectLatest(student.getId(), exam.getId()).getDetail()).contains("InvalidImage");
        nextStatus = 200;
        nextBody = "{\"Data\":{\"Confidence\":90.0}}";
    }

    @Test
    @DisplayName("厂商鉴权/限流失败 → UNAVAILABLE（拦下 + 教师放行），且不扣重试额度")
    void authFailureIsUnavailableNotStudentFault() {
        Exam exam = publishedExam();
        enroll();
        nextStatus = 200;
        nextBody = "{\"Code\":\"Throttling\",\"Message\":\"请求过于频繁\"}";
        FaceVerifyResultVO r = verify(exam.getId(), true);
        assertThat(r.getResult()).isEqualTo("UNAVAILABLE");
        assertThat(r.isNeedTeacherOverride()).isTrue();
        assertThat(logMapper.countFailedAttempts(student.getId(), exam.getId())).isZero();
        nextStatus = 200;
        nextBody = "{\"Data\":{\"Confidence\":90.0}}";
    }

    @Test
    @DisplayName("没录底片：开考被拦，且核验提示去录人脸")
    void blockedWhenNotEnrolled() {
        Exam exam = publishedExam();
        FaceVerifyResultVO r = verify(exam.getId(), true);
        assertThat(r.getResult()).isEqualTo("NEED_ENROLL");
        assertThatThrownBy(() -> examService.startExam(exam.getId(), student.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人脸核验");
    }

    @Test
    @DisplayName("录入后核验通过 → 可以开考，且答卷上固化了相似度")
    void passAllowsStart() {
        Exam exam = publishedExam();
        enroll();
        nextStatus = 200;
        nextBody = "{\"Data\":{\"Confidence\":90.0}}";
        FaceVerifyResultVO r = verify(exam.getId(), true);
        assertThat(r.isPassed()).isTrue();
        assertThat(r.getConfidence()).isEqualByComparingTo("90.00");

        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        ExamRecord saved = recordMapper.selectById(record.getId());
        assertThat(saved.getFaceVerified()).isEqualTo(1);
        assertThat(saved.getFaceConfidence()).isEqualByComparingTo("90.00");
        assertThat(saved.getFaceVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("相似度低于阈值：判失败并扣掉一次重试额度")
    void lowConfidenceFails() {
        Exam exam = publishedExam();
        enroll();
        nextBody = "{\"Data\":{\"Confidence\":40.0}}";
        FaceVerifyResultVO r1 = verify(exam.getId(), true);
        assertThat(r1.getResult()).isEqualTo("FAIL");
        assertThat(r1.isPassed()).isFalse();
        assertThat(r1.getRetriesLeft()).isEqualTo(2);
        assertThat(r1.getMessage()).contains("相似度");
        assertThatThrownBy(() -> examService.startExam(exam.getId(), student.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("连续失败到上限 → 第 3 次用完额度、第 4 次起禁考，并提示需要教师放行")
    void blockedAfterMaxRetries() {
        Exam exam = publishedExam();
        enroll();
        nextBody = "{\"Data\":{\"Confidence\":10.0}}";

        // maxRetries=3 的语义是「允许试 3 次」，所以前 3 次是 FAIL（额度递减），第 4 次才 BLOCKED
        FaceVerifyResultVO first = verify(exam.getId(), true);
        assertThat(first.getResult()).isEqualTo("FAIL");
        assertThat(first.getRetriesLeft()).isEqualTo(2);

        FaceVerifyResultVO second = verify(exam.getId(), true);
        assertThat(second.getRetriesLeft()).isEqualTo(1);

        FaceVerifyResultVO third = verify(exam.getId(), true);
        assertThat(third.getResult()).isEqualTo("FAIL");
        assertThat(third.getRetriesLeft()).isZero();
        assertThat(third.isBlocked()).isTrue();
        assertThat(third.getMessage()).contains("上限");

        // 第 4 次：直接 BLOCKED，且不再调用厂商接口（不再产生费用）
        int before = compareCalls.get();
        FaceVerifyResultVO fourth = verify(exam.getId(), true);
        assertThat(fourth.getResult()).isEqualTo("BLOCKED");
        assertThat(fourth.isBlocked()).isTrue();
        assertThat(fourth.isNeedTeacherOverride()).isTrue();
        assertThat(compareCalls.get()).isEqualTo(before);

        assertThatThrownBy(() -> examService.startExam(exam.getId(), student.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人脸核验");
    }

    @Test
    @DisplayName("厂商不可用：拦下、提示人工放行，但【不算】学生的失败次数")
    void unavailableBlocksButDoesNotCount() {
        Exam exam = publishedExam();
        enroll();
        nextStatus = 500;
        nextBody = "upstream boom";
        FaceVerifyResultVO r = verify(exam.getId(), true);
        assertThat(r.getResult()).isEqualTo("UNAVAILABLE");
        assertThat(r.isPassed()).isFalse();
        assertThat(r.isNeedTeacherOverride()).isTrue();
        assertThat(logMapper.countFailedAttempts(student.getId(), exam.getId())).isZero();
        assertThatThrownBy(() -> examService.startExam(exam.getId(), student.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        nextStatus = 200;
        nextBody = "{\"Data\":{\"Confidence\":90.0}}";
    }

    @Test
    @DisplayName("活体未过：直接判失败，且不调用厂商接口（省一次调用费）")
    void livenessFailSkipsApi() {
        Exam exam = publishedExam();
        enroll();
        int before = compareCalls.get();
        FaceVerifyResultVO r = verify(exam.getId(), false);
        assertThat(r.getResult()).isEqualTo("FAIL");
        assertThat(r.getMessage()).contains("眨一下眼");
        assertThat(compareCalls.get()).isEqualTo(before);
    }

    @Test
    @DisplayName("教师放行 → 未核验也能开考，相似度为空但留下 OVERRIDE_PASS 痕迹")
    void overrideAllowsStart() {
        Exam exam = publishedExam();
        enroll();
        faceService.override(student.getId(), exam.getId(), teacher.getId(), "现场监考已人工确认", "127.0.0.1");

        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        ExamRecord saved = recordMapper.selectById(record.getId());
        assertThat(saved.getFaceVerified()).isEqualTo(1);
        assertThat(saved.getFaceConfidence()).isNull();

        List<FaceVerifyLog> logs = logMapper.selectList(null);
        assertThat(logs).anyMatch(l -> FaceRecognitionService.RESULT_OVERRIDE_PASS.equals(l.getResult())
                && teacher.getId().equals(l.getOperatorId())
                && l.getDetail().contains("人工确认"));
    }

    @Test
    @DisplayName("核验结果有有效期：过期后重新被拦")
    void passWindowExpires() {
        Exam exam = publishedExam();
        enroll();
        nextBody = "{\"Data\":{\"Confidence\":99.0}}";
        assertThat(verify(exam.getId(), true).isPassed()).isTrue();

        // 把那条 PASS 记录的时间改到 10 分钟前（窗口是 5 分钟）
        FaceVerifyLog pass = logMapper.selectLatest(student.getId(), exam.getId());
        logMapper.update(null, new UpdateWrapper<FaceVerifyLog>()
                .eq("id", pass.getId())
                .set("created_at", LocalDateTime.now().minusMinutes(10)));

        assertThat(faceService.hasValidPass(student.getId(), exam.getId())).isFalse();
        assertThatThrownBy(() -> examService.startExam(exam.getId(), student.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人脸核验");
    }

    @Test
    @DisplayName("照片过大被拒（且不会写库）")
    void oversizedImageRejected() {
        enroll();
        FaceEnrollForm form = new FaceEnrollForm();
        form.setImageBase64("A".repeat(400001));
        form.setLiveness(true);
        assertThatThrownBy(() -> faceService.enroll(student.getId(), form, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("照片过大");
    }

    @Test
    @DisplayName("状态查询：录入、失败次数、阻断状态都如实反映")
    void statusReflectsState() {
        Exam exam = publishedExam();
        FaceStatusVO before = faceService.status(student.getId(), exam.getId());
        assertThat(before.isEnabled()).isTrue();
        assertThat(before.isEnrolled()).isFalse();

        enroll();
        nextBody = "{\"Data\":{\"Confidence\":10.0}}";
        verify(exam.getId(), true);

        FaceStatusVO after = faceService.status(student.getId(), exam.getId());
        assertThat(after.isEnrolled()).isTrue();
        assertThat(after.getFailedAttempts()).isEqualTo(1);
        assertThat(after.isPassed()).isFalse();
        assertThat(after.getLastResult()).isEqualTo("FAIL");
        assertThat(after.getMaxRetries()).isEqualTo(3);
    }
}