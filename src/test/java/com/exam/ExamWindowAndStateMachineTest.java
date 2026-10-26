package com.exam;

import com.exam.common.constant.ExamStatus;
import com.exam.common.constant.QuestionType;
import com.exam.common.exception.BusinessException;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamReviewDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.StudentAnswer;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamReviewService;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 考试时间窗、发布/结束状态机、以及考试归属校验。
 *
 * <p>三件事此前都不存在：{@code start_time/end_time} 是没人读的摆设字段；
 * publish/end 直接 set 状态，可以把「已结束」改回「进行中」；
 * 写操作只看「是不是教师」，教师之间可以互相操作对方的考试。</p>
 */
@AutoConfigureMockMvc
@DisplayName("考试时间窗 · 状态机 · 归属")
class ExamWindowAndStateMachineTest extends BaseIntegrationTest {

    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExamService examService;
    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamReviewService examReviewService;
    @Autowired
    private StudentAnswerMapper studentAnswerMapper;

    private Exam newDraftExam() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "窗试题", "A", 10);

        ExamGenerateDTO.RuleItem rule = new ExamGenerateDTO.RuleItem();
        rule.setType(QuestionType.SINGLE);
        rule.setCount(1);
        rule.setScore(10);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("窗测试_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(6);
        form.setRules(List.of(rule));

        return examRuleService.generateExam(form, teacher.getId());   // 草稿
    }

    private String tokenOf(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asText();
    }

    // ==================== 状态机 ====================

    @Test
    @DisplayName("发布：草稿 → 进行中，并补上开始时间（让时间窗字段真正有值）")
    void publish_setsStartTime() throws Exception {
        Exam draft = newDraftExam();

        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk());

        Exam after = examMapper.selectById(draft.getId());
        assertThat(after.getStatus()).isEqualTo(ExamStatus.IN_PROGRESS);
        assertThat(after.getStartTime()).as("没预设开始时间时，发布即开始").isNotNull();
    }

    @Test
    @DisplayName("【关键】已结束的考试不能重新发布回进行中")
    void publish_afterEnd_rejected() throws Exception {
        Exam draft = newDraftExam();
        String token = tokenOf(teacher.getUsername());

        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(put("/api/exams/" + draft.getId() + "/end")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        // 此前这里会静默成功：学生又能进入一场本该结束的考试
        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        assertThat(examMapper.selectById(draft.getId()).getStatus()).isEqualTo(ExamStatus.FINISHED);
    }

    @Test
    @DisplayName("重复发布 / 结束都返回 409，草稿不能直接结束")
    void illegalTransitions_rejected() throws Exception {
        Exam draft = newDraftExam();
        String token = tokenOf(teacher.getUsername());

        // 草稿不能直接结束
        mockMvc.perform(put("/api/exams/" + draft.getId() + "/end")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        // 重复发布
        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("结束：进行中 → 已结束，并记下结束时刻")
    void end_setsEndTime() throws Exception {
        Exam draft = newDraftExam();
        String token = tokenOf(teacher.getUsername());
        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        mockMvc.perform(put("/api/exams/" + draft.getId() + "/end")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Exam after = examMapper.selectById(draft.getId());
        assertThat(after.getStatus()).isEqualTo(ExamStatus.FINISHED);
        assertThat(after.getEndTime()).isNotNull();
    }

    // ==================== 归属（M4） ====================

    @Test
    @DisplayName("【关键】教师不能发布/结束别人创建的考试（403），管理员可以")
    void publish_othersExam_rejected() throws Exception {
        Exam draft = newDraftExam();   // teacher 创建
        User other = createUser("t2_" + unique(), "另一位老师", 1);
        User admin = createUser("admin_" + unique(), "管理员", 0);

        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                        .header("Authorization", "Bearer " + tokenOf(other.getUsername())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/exams/" + draft.getId() + "/publish")
                        .header("Authorization", "Bearer " + tokenOf(admin.getUsername())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("教师不能批改别人创建的考试（403），本人可以")
    void review_othersExam_rejected() {
        Exam exam = published(newDraftExam());
        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        ExamSubmitDTO submit = new ExamSubmitDTO();
        submit.setRecordId(record.getId());
        submit.setAnswers(Map.of());
        examService.submitExam(submit, student.getId(), "req-review-owner-" + unique());

        Long answerId = studentAnswerMapper.selectList(
                        new LambdaQueryWrapper<StudentAnswer>()
                                .eq(StudentAnswer::getRecordId, record.getId()))
                .get(0).getId();

        ExamReviewDTO.Item item = new ExamReviewDTO.Item();
        item.setAnswerId(answerId);
        item.setScore(0);
        ExamReviewDTO form = new ExamReviewDTO();
        form.setItems(List.of(item));

        User other = createUser("t3_" + unique(), "第三位老师", 1);
        assertThatThrownBy(() -> examReviewService.reviewAnswers(form, other.getId(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能批改自己创建的考试");

        // 本人批改照常成功（别把功能一起修坏）
        assertThat(examReviewService.reviewAnswers(form, teacher.getId(), false)).isNotNull();
    }

    // ==================== 时间窗（M7） ====================

    @Test
    @DisplayName("未到开始时间不能开考")
    void start_beforeWindow_rejected() {
        Exam exam = published(newDraftExam());
        exam.setStartTime(LocalDateTime.now().plusHours(1));
        examMapper.updateById(exam);

        assertThatThrownBy(() -> examService.startExam(exam.getId(), student.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未开始");
    }

    @Test
    @DisplayName("过了结束时间不能开考")
    void start_afterWindow_rejected() {
        Exam exam = published(newDraftExam());
        exam.setEndTime(LocalDateTime.now().minusMinutes(1));
        examMapper.updateById(exam);

        assertThatThrownBy(() -> examService.startExam(exam.getId(), student.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    @DisplayName("字段为空 = 该端不限制，照常可以开考（内置模拟卷就是这种）")
    void start_withoutWindow_allowed() {
        Exam exam = published(newDraftExam());

        assertThat(examService.startExam(exam.getId(), student.getId(), "127.0.0.1")).isNotNull();
    }

    @Test
    @DisplayName("到点自动结束：过了 end_time 的进行中考试会被标记为已结束")
    void autoEnd_expiredPublishedExam() {
        Exam exam = published(newDraftExam());
        exam.setEndTime(LocalDateTime.now().minusMinutes(1));
        examMapper.updateById(exam);

        int ended = examService.autoEndExpiredExams();

        assertThat(ended).isEqualTo(1);
        assertThat(examMapper.selectById(exam.getId()).getStatus()).isEqualTo(ExamStatus.FINISHED);
    }

    private Exam published(Exam exam) {
        exam.setStatus(ExamStatus.IN_PROGRESS);
        examMapper.updateById(exam);
        return examMapper.selectById(exam.getId());
    }
}