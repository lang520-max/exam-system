package com.exam;

import com.exam.common.constant.GradedBy;
import com.exam.common.constant.QuestionType;
import com.exam.common.constant.RecordStatus;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamReviewDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.dto.response.ExamReviewVO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamReviewService;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教师批改接口测试。
 *
 * <p>覆盖的是此前完全缺失的一环：主观题的分数<b>从来没有人能改</b>。
 * {@code GradingEngine.regrade()} 与 {@code reviewedBy/reviewedAt} 都是死代码，
 * 没有接口调用它们；接入 AI 评分后这个问题更突出——AI 判错也没人能纠正。</p>
 */
@AutoConfigureMockMvc
@DisplayName("教师批改")
class ExamReviewTest extends BaseIntegrationTest {

    private static final int OBJECTIVE_FULL = 5;
    private static final int SUBJECTIVE_FULL = 10;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExamService examService;
    @Autowired
    private ExamReviewService reviewService;
    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamRecordMapper recordMapper;
    @Autowired
    private StudentAnswerMapper answerMapper;

    private Exam exam;
    private Question objective;
    private Question subjective;
    private Question subjective2;
    private Long recordId;

    @BeforeEach
    void initExamAndSubmit() {
        objective = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "客观题", "A", OBJECTIVE_FULL);
        subjective = createQuestion(bank.getId(), QuestionType.FILL, 2, "主观题一", "标准答案", SUBJECTIVE_FULL);
        // 第二道主观题是必要的：只有一道时「评完它」就等于「没有剩余」，
        // 无法验证「还有题未评时记录应停在待判分」这条规则
        subjective2 = createQuestion(bank.getId(), QuestionType.FILL, 2, "主观题二", "标准答案二", SUBJECTIVE_FULL);

        ExamGenerateDTO.RuleItem r1 = new ExamGenerateDTO.RuleItem();
        r1.setType(QuestionType.SINGLE);
        r1.setCount(1);
        r1.setScore(OBJECTIVE_FULL);

        ExamGenerateDTO.RuleItem r2 = new ExamGenerateDTO.RuleItem();
        r2.setType(QuestionType.FILL);
        r2.setCount(2);
        r2.setScore(SUBJECTIVE_FULL);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("批改测试卷_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(10);
        form.setRules(List.of(r1, r2));

        Exam created = examRuleService.generateExam(form, teacher.getId());
        created.setStatus(1);
        examMapper.updateById(created);
        exam = examMapper.selectById(created.getId());

        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        Map<Long, String> answers = new HashMap<>();
        answers.put(objective.getId(), "A");
        answers.put(subjective.getId(), "第一题作答");
        answers.put(subjective2.getId(), "第二题作答");
        ExamSubmitDTO submit = new ExamSubmitDTO();
        submit.setRecordId(record.getId());
        submit.setAnswers(answers);
        examService.submitExam(submit, student.getId(), "req-" + unique());
        recordId = record.getId();
    }

    /** 测试档未启用 AI，因此主观题都处于「待复核」；这里取其中一道 */
    private StudentAnswer pendingAnswer() {
        return answerMapper.selectSubjectiveByRecordId(recordId).stream()
                .filter(a -> a.getQuestionId().equals(subjective.getId()))
                .findFirst().orElseThrow();
    }

    /** 该考生全部主观题答案的 id */
    private List<Long> allSubjectiveAnswerIds() {
        return answerMapper.selectSubjectiveByRecordId(recordId).stream()
                .map(StudentAnswer::getId)
                .toList();
    }

    private Map<String, Object> grade(Long answerId, int score) {
        ExamReviewDTO.Item item = new ExamReviewDTO.Item();
        item.setAnswerId(answerId);
        item.setScore(score);
        ExamReviewDTO form = new ExamReviewDTO();
        form.setItems(List.of(item));
        return reviewService.reviewAnswers(form, teacher.getId());
    }

    private Map<String, Object> gradeAll(int score) {
        List<ExamReviewDTO.Item> items = allSubjectiveAnswerIds().stream().map(id -> {
            ExamReviewDTO.Item item = new ExamReviewDTO.Item();
            item.setAnswerId(id);
            item.setScore(score);
            return item;
        }).toList();
        ExamReviewDTO form = new ExamReviewDTO();
        form.setItems(items);
        return reviewService.reviewAnswers(form, teacher.getId());
    }

    // ---------------- 批改视图 ----------------

    @Test
    @DisplayName("教师能拿到全班的逐题作答，含待复核题数")
    void reviewSheet_containsAllStudentsAndAnswers() {
        ExamReviewVO sheet = reviewService.getReviewSheet(exam.getId());

        assertThat(sheet.getExamTitle()).isEqualTo(exam.getTitle());
        assertThat(sheet.getRecords()).hasSize(1);

        ExamReviewVO.StudentRecord row = sheet.getRecords().get(0);
        assertThat(row.getRecordId()).isEqualTo(recordId);
        assertThat(row.getStudentName()).isNotBlank();
        assertThat(row.getAnswers()).hasSize(3);

        // 主观题待复核、客观题已自动判分 —— 这正是教师要看到的信息
        ExamReviewVO.AnswerItem sub = row.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals(subjective.getId()))
                .findFirst().orElseThrow();
        assertThat(sub.getPendingReview()).isTrue();
        assertThat(sub.getGradedBy()).isNull();
        assertThat(sub.getFullScore()).isEqualTo(SUBJECTIVE_FULL);
        assertThat(sub.getAnswerId()).isNotNull();

        ExamReviewVO.AnswerItem obj = row.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals(objective.getId()))
                .findFirst().orElseThrow();
        assertThat(obj.getPendingReview()).isFalse();
        assertThat(obj.getGradedBy()).isEqualTo(GradedBy.AUTO);

        assertThat(sheet.getPendingTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("学生调用批改视图返回 403")
    void reviewSheet_asStudent_returns403() throws Exception {
        String token = loginAs(student.getUsername());

        mockMvc.perform(get("/api/exams/" + exam.getId() + "/review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教师调用批改视图返回 200")
    void reviewSheet_asTeacher_succeeds() throws Exception {
        String token = loginAs(teacher.getUsername());

        mockMvc.perform(get("/api/exams/" + exam.getId() + "/review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ---------------- 改分 ----------------

    @Test
    @DisplayName("教师给分后落库 graded_by=TEACHER 并记录复核人")
    void grading_persistsTeacherAndReviewer() {
        Long answerId = pendingAnswer().getId();

        grade(answerId, 7);

        StudentAnswer after = answerMapper.selectById(answerId);
        assertThat(after.getScore()).isEqualTo(7);
        assertThat(after.getGradedBy()).isEqualTo(GradedBy.TEACHER);
        assertThat(after.getReviewedBy()).isEqualTo(teacher.getId());
        assertThat(after.getReviewedAt()).isNotNull();
        // 部分分 → 不是「完全正确」，但也不是「答错」
        assertThat(after.getIsCorrect()).isEqualTo(2);
    }

    @Test
    @DisplayName("改分后该考生总分被重算")
    void grading_recalculatesRecordTotal() {
        grade(pendingAnswer().getId(), 6);

        // 客观题 5 分 + 主观题 6 分
        assertThat(recordMapper.selectById(recordId).getScore()).isEqualTo(5 + 6);
    }

    @Test
    @DisplayName("还有题未评时记录停在「待判分」")
    void grading_leavesRecordPendingWhenOthersRemain() {
        grade(pendingAnswer().getId(), 6);

        assertThat(recordMapper.selectById(recordId).getStatus())
                .isEqualTo(RecordStatus.PENDING_GRADE);
    }

    @Test
    @DisplayName("全部评完后记录变为「已出分」")
    void grading_completesRecordWhenNothingPending() {
        gradeAll(SUBJECTIVE_FULL);

        ExamRecord record = recordMapper.selectById(recordId);
        assertThat(record.getStatus()).isEqualTo(RecordStatus.GRADED);
        // 客观 5 + 两道主观各 10
        assertThat(record.getScore()).isEqualTo(OBJECTIVE_FULL + SUBJECTIVE_FULL * 2);
        // 这份卷子现在及格了
        assertThat(record.getScore()).isGreaterThanOrEqualTo(exam.getPassScore());
    }

    @Test
    @DisplayName("给满分判为「正确」")
    void fullMarks_markedCorrect() {
        Long answerId = pendingAnswer().getId();

        grade(answerId, SUBJECTIVE_FULL);

        assertThat(answerMapper.selectById(answerId).getIsCorrect()).isEqualTo(1);
    }

    // ---------------- 边界与安全 ----------------

    @Test
    @DisplayName("分数超过本题满分时被裁剪，不会把总分撑爆")
    void scoreAboveFull_isClamped() {
        Long answerId = pendingAnswer().getId();

        grade(answerId, 999);

        assertThat(answerMapper.selectById(answerId).getScore()).isEqualTo(SUBJECTIVE_FULL);
        assertThat(recordMapper.selectById(recordId).getScore())
                .isEqualTo(OBJECTIVE_FULL + SUBJECTIVE_FULL);
    }

    @Test
    @DisplayName("分数为负时被裁剪到 0")
    void negativeScore_isClampedToZero() {
        Long answerId = pendingAnswer().getId();

        grade(answerId, -20);

        assertThat(answerMapper.selectById(answerId).getScore()).isZero();
    }

    @Test
    @DisplayName("教师可以覆盖 AI 已经给出的分数")
    void canOverrideAiScore() {
        Long answerId = pendingAnswer().getId();
        // 模拟 AI 已经打了 8 分（部分正确）
        answerMapper.updateAiScore(answerId, 8, 2, "AI 认为基本正确", "test-model");
        assertThat(answerMapper.selectById(answerId).getGradedBy()).isEqualTo(GradedBy.AI);

        grade(answerId, 3);

        StudentAnswer after = answerMapper.selectById(answerId);
        assertThat(after.getScore()).isEqualTo(3);
        assertThat(after.getGradedBy()).isEqualTo(GradedBy.TEACHER);
    }

    @Test
    @DisplayName("【关键】能覆盖已出分记录上的分数")
    void canRegradeAlreadyGradedRecord() {
        Long answerId = pendingAnswer().getId();
        gradeAll(SUBJECTIVE_FULL);                 // 先全部评完 → 已出分
        assertThat(recordMapper.selectById(recordId).getStatus()).isEqualTo(RecordStatus.GRADED);

        // 再改一次分。若沿用只允许「待判分 → 已出分」的条件更新，这次会静默失效：
        // 分改到了答案上，总分却没变 —— 最难查的一类 bug
        grade(answerId, 4);

        assertThat(answerMapper.selectById(answerId).getScore()).isEqualTo(4);
        // 客观 5 + 改后 4 + 另一道主观 10
        assertThat(recordMapper.selectById(recordId).getScore())
                .isEqualTo(OBJECTIVE_FULL + 4 + SUBJECTIVE_FULL);
        assertThat(recordMapper.selectById(recordId).getStatus()).isEqualTo(RecordStatus.GRADED);
    }

    @Test
    @DisplayName("尚未交卷的考生不能被批改（否则会提前结束他的考试）")
    void ongoingRecord_isSkipped() {
        // 开一场新考试但不交卷
        ExamRecord ongoing = examService.startExam(exam.getId(), otherStudent().getId(), "127.0.0.1");
        StudentAnswer orphan = new StudentAnswer();
        orphan.setRecordId(ongoing.getId());
        orphan.setQuestionId(subjective.getId());
        orphan.setAnswer("还在写");
        orphan.setScore(0);
        orphan.setIsCorrect(0);
        answerMapper.insert(orphan);

        Map<String, Object> result = grade(orphan.getId(), 5);

        assertThat(result.get("updatedCount")).isEqualTo(0);
        assertThat((List<?>) result.get("skipped")).isNotEmpty();
        // 记录状态没被改动
        assertThat(recordMapper.selectById(ongoing.getId()).getStatus()).isEqualTo(RecordStatus.ONGOING);
    }

    @Test
    @DisplayName("不存在的答案被跳过而不是整批失败")
    void unknownAnswer_isSkippedWithoutFailing() {
        ExamReviewDTO.Item bad = new ExamReviewDTO.Item();
        bad.setAnswerId(99999999L);
        bad.setScore(5);

        ExamReviewDTO.Item good = new ExamReviewDTO.Item();
        good.setAnswerId(pendingAnswer().getId());
        good.setScore(6);

        ExamReviewDTO form = new ExamReviewDTO();
        form.setItems(List.of(bad, good));

        Map<String, Object> result = reviewService.reviewAnswers(form, teacher.getId());

        assertThat(result.get("updatedCount")).isEqualTo(1);
        assertThat((List<?>) result.get("skipped")).hasSize(1);
        assertThat(answerMapper.selectById(good.getAnswerId()).getScore()).isEqualTo(6);
    }

    @Test
    @DisplayName("批量批改：多题一次提交并各自重算")
    void bulkGrading_updatesAllAffectedRecords() {
        Long answerId = pendingAnswer().getId();

        ExamReviewDTO.Item i1 = new ExamReviewDTO.Item();
        i1.setAnswerId(answerId);
        i1.setScore(9);
        ExamReviewDTO form = new ExamReviewDTO();
        form.setItems(List.of(i1));

        Map<String, Object> result = reviewService.reviewAnswers(form, teacher.getId());

        assertThat(result.get("updatedCount")).isEqualTo(1);
        assertThat((List<?>) result.get("records")).hasSize(1);
    }

    // ---------------- helper ----------------

    private com.exam.entity.User otherStudent() {
        return createUser("s2_" + unique(), "STUDENT2", 2);
    }

    /** 用真实登录接口换 JWT */
    private String loginAs(String username) throws Exception {
        var result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/auth/login")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"%s\",\"password\":\"123456\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("data").get("accessToken").asText();
    }
}