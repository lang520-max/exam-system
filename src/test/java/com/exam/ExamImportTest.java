package com.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.exception.BusinessException;
import com.exam.dto.request.ExamImportDTO;
import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.entity.QuestionBank;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.service.ExamImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 试卷 JSON 导入。
 *
 * <p>覆盖三件事：<b>能不能导进来</b>（题目/选项三种写法、引用已有题、多卷）、
 * <b>错误提示有没有用</b>（一次列全、带题号下标）、
 * <b>出错了会不会留下半成品</b>（事务原子性）。</p>
 */
@AutoConfigureMockMvc
@DisplayName("试卷导入 · JSON 导入")
class ExamImportTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamImportService examImportService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String PASSWORD = "123456";

    // ==================== 正常导入 ====================

    @Test
    @DisplayName("单卷导入：新建题目、落库为草稿、总分与快照正确")
    void importSinglePaper_createsDraftExam() throws Exception {
        String body = """
                {
                  "bankId": %d,
                  "title": "导入测试卷",
                  "description": "由 JSON 导入",
                  "duration": 90,
                  "passScore": 60,
                  "questions": [
                    {"type":0,"difficulty":1,"score":30,"content":"1+1=?",
                     "options":{"A":"1","B":"2","C":"3","D":"4"},"answer":"B","analysis":"常识"},
                    {"type":1,"difficulty":2,"score":30,"content":"以下哪些是偶数？",
                     "options":[{"label":"A","content":"2"},{"label":"B","content":"3"},
                                {"label":"C","content":"4"},{"label":"D","content":"5"}],
                     "answer":"CA","analysis":"2 和 4 是偶数"},
                    {"type":2,"difficulty":1,"score":10,"content":"地球是球体。","answer":"A"},
                    {"type":3,"difficulty":2,"score":15,"content":"水的化学式是____。","answer":"H2O"},
                    {"type":4,"difficulty":3,"score":15,"content":"简述牛顿第一定律。",
                     "answer":"物体在不受外力时保持静止或匀速直线运动。"}
                  ]
                }
                """.formatted(bank.getId());

        MvcResult result = mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.importedExams").value(1))
                .andExpect(jsonPath("$.data.createdQuestions").value(5))
                .andExpect(jsonPath("$.data.exams[0].questionCount").value(5))
                .andExpect(jsonPath("$.data.exams[0].totalScore").value(100))
                .andExpect(jsonPath("$.data.exams[0].passScore").value(60))
                // 导入必须是草稿：未经教师检查的卷子不能直接给学生看
                .andExpect(jsonPath("$.data.exams[0].status").value(0))
                .andReturn();

        long examId = objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("exams").get(0).path("id").asLong();

        Exam exam = examMapper.selectById(examId);
        assertThat(exam.getStatus()).isZero();
        assertThat(exam.getTotalScore()).isEqualTo(100);
        assertThat(exam.getCreatedBy()).isEqualTo(teacher.getId());
        assertThat(exam.getQuestionSnapshot()).contains("\"score\":30");

        List<Question> saved = questionsOf(bank.getId());
        assertThat(saved).hasSize(5);

        // 多选答案统一按字母升序存储，与判分引擎的比较口径一致
        Question multi = saved.stream().filter(q -> q.getType() == 1).findFirst().orElseThrow();
        assertThat(multi.getAnswer()).isEqualTo("AC");

        // 判断题不用写选项，系统自动补「正确 / 错误」
        Question judge = saved.stream().filter(q -> q.getType() == 2).findFirst().orElseThrow();
        assertThat(judge.getOptions()).contains("正确").contains("错误");
        assertThat(judge.getAnswer()).isEqualTo("A");

        // 填空/简答是主观题，不写选项
        Question essay = saved.stream().filter(q -> q.getType() == 4).findFirst().orElseThrow();
        assertThat(essay.getOptions()).isNull();
    }

    @Test
    @DisplayName("options 写成 [[\"A\",\"内容\"]] 也接受（便于手写）")
    void importOptionsAsArrayOfPairs_isAccepted() throws Exception {
        String body = """
                {"bankId":%d,"title":"数组式选项卷","duration":60,"questions":[
                  {"type":0,"difficulty":1,"score":100,"content":"2×3=?",
                   "options":[["A","5"],["B","6"],["C","7"],["D","8"]],"answer":"B"}
                ]}
                """.formatted(bank.getId());

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exams[0].totalScore").value(100))
                // 没给 passScore，按总分 60% 自动计算
                .andExpect(jsonPath("$.data.exams[0].passScore").value(60));

        Question q = questionsOf(bank.getId()).get(0);
        assertThat(q.getOptions()).isEqualTo(
                "[{\"label\":\"A\",\"content\":\"5\"},{\"label\":\"B\",\"content\":\"6\"},"
                        + "{\"label\":\"C\",\"content\":\"7\"},{\"label\":\"D\",\"content\":\"8\"}]");
    }

    @Test
    @DisplayName("引用题库已有题目：可用 score 覆盖本卷卷面分值")
    void importExistingQuestionId_overridesPaperScore() throws Exception {
        Question existing = createQuestion(bank.getId(), 0, 1, "已有题目", "A", 5);
        String body = """
                {"bankId":%d,"title":"引用已有题的卷子","duration":60,"questions":[
                  {"questionId":%d,"score":42}
                ]}
                """.formatted(bank.getId(), existing.getId());

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // 没有新建题目，只是引用
                .andExpect(jsonPath("$.data.createdQuestions").value(0))
                .andExpect(jsonPath("$.data.exams[0].questionCount").value(1))
                .andExpect(jsonPath("$.data.exams[0].totalScore").value(42))
                .andExpect(jsonPath("$.data.exams[0].passScore").value(26));

        assertThat(questionsOf(bank.getId())).hasSize(1);
    }

    @Test
    @DisplayName("多卷导入：一次导入两份草稿")
    void importMultipleExams_createsTwoDrafts() throws Exception {
        String body = """
                {"bankId":%d,"exams":[
                  {"title":"卷A","duration":60,"questions":[
                    {"type":0,"difficulty":1,"score":100,"content":"A卷题",
                     "options":{"A":"对","B":"错"},"answer":"A"}]},
                  {"title":"卷B","duration":45,"questions":[
                    {"type":2,"difficulty":1,"score":100,"content":"B卷判断题","answer":"B"}]}
                ]}
                """.formatted(bank.getId());

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedExams").value(2))
                .andExpect(jsonPath("$.data.exams[0].title").value("卷A"))
                .andExpect(jsonPath("$.data.exams[1].title").value("卷B"))
                .andExpect(jsonPath("$.data.exams[1].status").value(0));
    }

    @Test
    @DisplayName("只给 bank.name 时自动新建题库")
    void importWithBankName_createsBank() throws Exception {
        String bankName = "导入新建题库_" + unique();
        String body = """
                {"bank":{"name":"%s","subject":"临时科目"},"title":"新库卷子","duration":60,"questions":[
                  {"type":0,"difficulty":1,"score":100,"content":"占位题",
                   "options":{"A":"对","B":"错"},"answer":"A"}
                ]}
                """.formatted(bankName);

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<QuestionBank> created = bankMapper.selectList(
                new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getName, bankName));
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getSubject()).isEqualTo("临时科目");
        assertThat(created.get(0).getCreatedBy()).isEqualTo(teacher.getId());
    }

    // ==================== 错误处理 ====================

    @Test
    @DisplayName("校验失败返回 400，并一次列全所有问题（带题号下标）")
    void importWithInvalidQuestions_returnsAllProblems() throws Exception {
        long examBefore = examMapper.selectCount(null);
        String body = """
                {"bankId":%d,"title":"坏卷","duration":60,"questions":[
                  {"type":0,"score":10,"content":"答案越界","options":{"A":"a","B":"b"},"answer":"D"},
                  {"type":1,"score":10,"content":"多选只给一个字母","options":{"A":"a","B":"b"},"answer":"A"},
                  {"type":2,"score":10,"content":"判断题答案非法","answer":"C"},
                  {"type":7,"score":10,"content":"题型非法","answer":"A"},
                  {"type":0,"score":10,"content":"缺答案","options":{"A":"a","B":"b"}}
                ]}
                """.formatted(bank.getId());

        MvcResult result = mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("第 1 份试卷「坏卷」第 1 题")))
                .andExpect(jsonPath("$.message", containsString("未定义的选项")))
                .andExpect(jsonPath("$.message", containsString("多选题答案至少需要 2 个字母")))
                .andExpect(jsonPath("$.message", containsString("判断题答案只能是 A")))
                .andExpect(jsonPath("$.message", containsString("type 必须是 0-4")))
                .andExpect(jsonPath("$.message", containsString("answer 不能为空")))
                .andReturn();

        // 5 条问题应该都被收集到，而不是遇到第一个就中断
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("共 5 个问题");
        assertThat(examMapper.selectCount(null)).isEqualTo(examBefore);
    }

    @Test
    @DisplayName("同一批里只要有一题不合法，整批都不落库（事务原子性）")
    void importWithOneInvalidQuestion_rollsBackWholeBatch() throws Exception {
        String bankName = "原子性测试题库_" + unique();
        long examBefore = examMapper.selectCount(null);

        // 用 REQUIRES_NEW 开独立事务：服务内部抛异常时的回滚是「真回滚」。
        // 若直接在测试事务里断言，写入的行在回滚前依然可见，
        // 「没有留下半成品」这条断言会假通过。
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> tx.execute(status -> {
            QuestionBank b = new QuestionBank();
            b.setName(bankName);
            b.setSubject("测试");
            b.setCreatedBy(teacher.getId());
            b.setCreatedAt(LocalDateTime.now());
            bankMapper.insert(b);

            ExamImportDTO dto = new ExamImportDTO();
            dto.setBankId(b.getId());
            dto.setTitle("半途失败的卷子");
            dto.setDuration(60);
            dto.setQuestions(List.of(
                    newQuestion(0, 50, "合法题", "A"),
                    newQuestion(0, 50, "非法题：答案越界", "D")));

            examImportService.importPapers(dto, teacher.getId());
            return null;
        })).isInstanceOf(BusinessException.class);

        assertThat(bankMapper.selectCount(
                new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getName, bankName))).isZero();
        assertThat(examMapper.selectCount(null)).isEqualTo(examBefore);
    }

    @Test
    @DisplayName("及格分高于总分返回 400")
    void importPassScoreAboveTotal_returns400() throws Exception {
        String body = """
                {"bankId":%d,"title":"及格分越界","duration":60,"passScore":200,"questions":[
                  {"type":0,"difficulty":1,"score":10,"content":"题","options":{"A":"a","B":"b"},"answer":"A"}
                ]}
                """.formatted(bank.getId());

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不能高于试卷总分")));
    }

    @Test
    @DisplayName("引用别的题库的题目返回 400（不允许跨题库组卷）")
    void importCrossBankQuestion_returns400() throws Exception {
        QuestionBank other = createBank("别的题库_" + unique(), teacher.getId());
        Question foreign = createQuestion(other.getId(), 0, 1, "别的题库的题", "A", 5);

        String body = """
                {"bankId":%d,"title":"跨库卷子","duration":60,"questions":[
                  {"questionId":%d}
                ]}
                """.formatted(bank.getId(), foreign.getId());

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不属于目标题库")));
    }

    @Test
    @DisplayName("questions 与 exams 同时传返回 400（两种写法互斥）")
    void importQuestionsAndExamsTogether_returns400() throws Exception {
        String body = """
                {"bankId":%d,"title":"混用","duration":60,
                 "questions":[{"type":0,"difficulty":1,"score":10,"content":"题",
                               "options":{"A":"a","B":"b"},"answer":"A"}],
                 "exams":[{"title":"另一份","duration":60,"questions":[
                    {"type":0,"difficulty":1,"score":10,"content":"题",
                     "options":{"A":"a","B":"b"},"answer":"A"}]}]}
                """.formatted(bank.getId());

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不能同时使用")));
    }

    @Test
    @DisplayName("题库与 bank 都不给返回 400")
    void importWithoutBank_returns400() throws Exception {
        String body = """
                {"title":"没指定题库","duration":60,"questions":[
                  {"type":0,"difficulty":1,"score":100,"content":"题",
                   "options":{"A":"a","B":"b"},"answer":"A"}]}
                """;

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("请指定目标题库")));
    }

    @Test
    @DisplayName("学生调用导入接口返回 403")
    void importAsStudent_returns403() throws Exception {
        String body = """
                {"bankId":%d,"title":"学生想导卷","duration":60,"questions":[
                  {"type":0,"difficulty":1,"score":100,"content":"题",
                   "options":{"A":"a","B":"b"},"answer":"A"}]}
                """.formatted(bank.getId());

        mockMvc.perform(post("/api/exams/import")
                        .header("Authorization", "Bearer " + tokenOf(student.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== 辅助 ====================

    private List<Question> questionsOf(Long bankId) {
        return questionMapper.selectList(
                new LambdaQueryWrapper<Question>().eq(Question::getBankId, bankId));
    }

    /** 构造一道选择题（选项 A/B），供服务层直调用例使用 */
    private ExamImportDTO.ImportQuestion newQuestion(int type, int score, String content, String answer) {
        ExamImportDTO.ImportQuestion q = new ExamImportDTO.ImportQuestion();
        q.setType(type);
        q.setDifficulty(1);
        q.setScore(score);
        q.setContent(content);
        q.setAnswer(answer);
        Map<String, String> opts = new LinkedHashMap<>();
        opts.put("A", "选项A");
        opts.put("B", "选项B");
        q.setOptions(opts);
        return q;
    }

    /** 走真实登录换 JWT：与浏览器里跑的链路一致 */
    private String tokenOf(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        // 必须显式指定 UTF-8：MockHttpServletResponse 默认按 ISO-8859-1 解码
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asText();
    }
}