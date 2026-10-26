package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.common.constant.RecordStatus;
import com.exam.common.exception.BusinessException;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 答案暂存（自动保存）与交卷合并。
 *
 * <p>覆盖两类风险：<b>做没做</b>（暂存能写、能读回、超时会清掉），
 * 以及加暂存时最容易踩的坑 —— 交卷仍在用 {@code batchInsert}，
 * 而暂存行已经在表里，于是撞上唯一键 {@code (record_id, question_id)}：
 * 学生一点交卷就是 500，答案还全丢。</p>
 */
@AutoConfigureMockMvc
@DisplayName("答案暂存 · 自动保存与交卷合并")
class AnswerDraftTest extends BaseIntegrationTest {

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
    private ExamRecordMapper recordMapper;
    @Autowired
    private StudentAnswerMapper answerMapper;

    private Exam exam;
    private List<Question> paper;

    @BeforeEach
    void initExam() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "暂存题一", "A", 10);
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "暂存题二", "B", 10);

        ExamGenerateDTO.RuleItem rule = new ExamGenerateDTO.RuleItem();
        rule.setType(QuestionType.SINGLE);
        rule.setCount(2);
        rule.setScore(10);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("暂存测试_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(12);
        form.setRules(List.of(rule));

        Exam created = examRuleService.generateExam(form, teacher.getId());
        created.setStatus(1);
        examMapper.updateById(created);
        exam = examMapper.selectById(created.getId());
        paper = examRuleService.getExamQuestions(exam);
    }

    private ExamRecord start() {
        return examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
    }

    @Test
    @DisplayName("暂存能写能读回（刷新页面后作答不丢）")
    void draft_roundTrip() {
        ExamRecord record = start();
        Map<Long, String> first = new HashMap<>();
        first.put(paper.get(0).getId(), "A");
        first.put(paper.get(1).getId(), "C");

        Map<String, Object> saved = examService.saveDraftAnswers(record.getId(), student.getId(), first);
        assertThat(saved.get("saved")).isEqualTo(2);

        Map<String, Object> readBack = examService.getDraftAnswers(record.getId(), student.getId());
        @SuppressWarnings("unchecked")
        Map<String, String> answers = (Map<String, String>) readBack.get("answers");
        assertThat(answers).hasSize(2);
        assertThat(answers.get(String.valueOf(paper.get(0).getId()))).isEqualTo("A");
        assertThat(answers.get(String.valueOf(paper.get(1).getId()))).isEqualTo("C");

        // 暂存不得产生任何「评分结论」
        for (StudentAnswer sa : rowsOf(record.getId())) {
            assertThat(sa.getGradedBy()).as("暂存不等于给自己打分").isNull();
            assertThat(sa.getScore()).isZero();
        }
    }

    @Test
    @DisplayName("【关键】已暂存过再交卷：不撞唯一键，且以交卷时的最终答案为准")
    void submit_afterDraft_overwritesAndDoesNotCrash() {
        ExamRecord record = start();

        // 刻意按「题号 → 标准答案」查出来，而不是假设 paper.get(0) 就是答案 A 的那道题：
        // generateExam 会把快照顺序打乱，靠下标猜答案的写法会 50% 概率失败。
        Map<Long, String> correct = correctAnswers();

        // 暂存时故意全答错
        Map<Long, String> drafted = new HashMap<>();
        paper.forEach(q -> drafted.put(q.getId(), wrongAnswerFor(correct.get(q.getId()))));
        examService.saveDraftAnswers(record.getId(), student.getId(), drafted);

        // 交卷时改成全对 —— 最终答案必须覆盖暂存
        ExamSubmitDTO submit = new ExamSubmitDTO();
        submit.setRecordId(record.getId());
        submit.setAnswers(correct);
        examService.submitExam(submit, student.getId(), "req-draft-then-submit-" + unique());

        ExamRecord after = recordMapper.selectById(record.getId());
        assertThat(after.getStatus()).isEqualTo(RecordStatus.GRADED);
        assertThat(after.getScore()).as("最终答案覆盖了暂存：两题都对 = 20 分").isEqualTo(20);
        assertThat(rowsOf(record.getId())).hasSize(2);   // 没有因重复插入多出行
    }

    @Test
    @DisplayName("交卷之后不允许再暂存（否则成绩单与判分会对不上）")
    void draft_afterSubmit_rejected() {
        ExamRecord record = start();
        ExamSubmitDTO submit = new ExamSubmitDTO();
        submit.setRecordId(record.getId());
        submit.setAnswers(Map.of(paper.get(0).getId(), "A"));
        examService.submitExam(submit, student.getId(), "req-submitted-" + unique());

        Map<Long, String> later = Map.of(paper.get(0).getId(), "D");

        assertThatThrownBy(() -> examService.saveDraftAnswers(record.getId(), student.getId(), later))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已交卷");
        assertThatThrownBy(() -> examService.getDraftAnswers(record.getId(), student.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已交卷");
    }

    @Test
    @DisplayName("暂存不属于本卷的题目会被忽略（不能凭空多出几道题）")
    void draft_ignoresForeignQuestions() {
        ExamRecord record = start();
        Map<Long, String> weird = new HashMap<>();
        weird.put(999_999L, "A");

        Map<String, Object> saved = examService.saveDraftAnswers(record.getId(), student.getId(), weird);

        assertThat(saved.get("saved")).isEqualTo(0);
        assertThat(rowsOf(record.getId())).isEmpty();
    }

    @Test
    @DisplayName("别人的考试记录不能暂存（403）")
    void draft_ofOthersRecord_rejected() {
        ExamRecord record = start();
        Map<Long, String> values = Map.of(paper.get(0).getId(), "A");

        assertThatThrownBy(() -> examService.saveDraftAnswers(record.getId(), teacher.getId(), values))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能");
    }

    @Test
    @DisplayName("超时未交卷时，暂存的作答会被一起清掉")
    void timeoutSweep_removesDrafts() {
        ExamRecord record = start();
        examService.saveDraftAnswers(record.getId(), student.getId(),
                Map.of(paper.get(0).getId(), "A"));
        assertThat(rowsOf(record.getId())).isNotEmpty();

        // 回拨开考时间到远超考试时长，然后跑清理任务
        recordMapper.update(null, new LambdaUpdateWrapper<ExamRecord>()
                .eq(ExamRecord::getId, record.getId())
                .set(ExamRecord::getStartTime, LocalDateTime.now().minusMinutes(120)));
        examService.sweepTimeoutRecords();

        assertThat(recordMapper.selectById(record.getId()).getStatus()).isEqualTo(RecordStatus.TIMEOUT);
        assertThat(rowsOf(record.getId()))
                .as("「超时」= 学生没提交；留下暂存行会让成绩单显示他写过的内容而分数是空的")
                .isEmpty();
    }

    @Test
    @DisplayName("接口层：POST 暂存 → GET 读回；别人的记录 403")
    void draftEndpoints_work() throws Exception {
        ExamRecord record = start();
        String token = tokenOf(student.getUsername());
        Long qid = paper.get(0).getId();

        mockMvc.perform(post("/api/exams/records/" + record.getId() + "/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"" + qid + "\":\"A\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(1));

        mockMvc.perform(get("/api/exams/records/" + record.getId() + "/draft")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers." + qid).value("A"));

        // 另一位学生碰不到这份记录
        mockMvc.perform(post("/api/exams/records/" + record.getId() + "/draft")
                        .header("Authorization", "Bearer " + tokenOf(student02().getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"" + qid + "\":\"B\"}}"))
                .andExpect(status().isForbidden());
    }

    /** 另造一个学生账号（BaseIntegrationTest 只提供 teacher / student） */
    private com.exam.entity.User student02() {
        return createUser("s2_" + unique(), "另一位同学", 2);
    }

    private String tokenOf(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"123456\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asText();
    }

    /** 题号 → 标准答案（按 id 查，不依赖快照顺序） */
    private Map<Long, String> correctAnswers() {
        Map<Long, String> out = new HashMap<>();
        for (Question q : paper) {
            out.put(q.getId(), questionMapper.selectById(q.getId()).getAnswer());
        }
        return out;
    }

    /** 给一个一定错的选项字母（选项是 A-D） */
    private static String wrongAnswerFor(String correct) {
        return "A".equals(correct) ? "D" : "A";
    }

    private List<StudentAnswer> rowsOf(Long recordId) {
        return answerMapper.selectList(
                new LambdaQueryWrapper<StudentAnswer>().eq(StudentAnswer::getRecordId, recordId));
    }
}