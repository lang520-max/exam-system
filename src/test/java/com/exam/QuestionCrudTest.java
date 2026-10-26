package com.exam;

import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.entity.QuestionBank;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 题库 / 题目管理接口。
 *
 * <p>覆盖三块此前没有防线的地方：</p>
 * <ol>
 *   <li><b>参数校验</b>：控制器直接绑定无注解的实体时 {@code @Valid} 形同虚设，
 *       脏题（缺题型、0 分、答案不在选项里）能一路落库，到判分阶段才暴露；</li>
 *   <li><b>归属校验</b>：写操作必须限定在「自己创建的」对象上，否则教师 A 能改删教师 B 的题；</li>
 *   <li><b>删除保护</b>：被试卷快照引用的题一旦物理删除，已发布的卷子会静默少一道题。</li>
 * </ol>
 *
 * <p>全部走真实过滤器链 + 真实登录换 JWT（不用 {@code @WithMockUser}），
 * 与浏览器里的链路一致。</p>
 */
@AutoConfigureMockMvc
@DisplayName("题库管理 · 校验 / 归属 / 删除保护")
class QuestionCrudTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExamMapper examMapper;

    private static final String PASSWORD = "123456";

    /** 选项 A/B 的标准 JSON 字符串（前端就是按这个形状提交的） */
    private static final String OPTIONS_AB =
            "[{\"label\":\"A\",\"content\":\"1\"},{\"label\":\"B\",\"content\":\"2\"}]";

    // ==================== 1. 参数校验 ====================

    @Test
    @DisplayName("缺题型返回 400（此前 @Valid 空转，能建出 type=null 的脏题）")
    void createQuestion_withoutType_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.remove("type");

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("type")));
    }

    @Test
    @DisplayName("题型越界（type=5）返回 400")
    void createQuestion_typeOutOfRange_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("type", 5);

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("分值为 0 返回 400（0 分题会让卷面总分与题数对不上）")
    void createQuestion_zeroScore_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("score", 0);

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("score")));
    }

    @Test
    @DisplayName("题干为空返回 400")
    void createQuestion_blankContent_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("content", "   ");

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("content")));
    }

    @Test
    @DisplayName("单选答案含未定义的选项返回 400（否则学生怎么选都算错）")
    void createQuestion_answerNotInOptions_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("answer", "D");

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("未定义的选项")));
    }

    @Test
    @DisplayName("多选只给一个字母返回 400（与导入路径同一口径）")
    void createQuestion_multiChoiceWithOneLetter_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("type", 1);
        body.put("answer", "A");

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("多选题答案至少需要 2 个字母")));
    }

    @Test
    @DisplayName("选择题缺选项返回 400")
    void createQuestion_choiceWithoutOptions_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.remove("options");

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("必须提供选项")));
    }

    @Test
    @DisplayName("判断题答案不是 A/B 返回 400")
    void createQuestion_judgeWithInvalidAnswer_returns400() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("type", 2);
        body.remove("options");
        body.put("answer", "C");

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("判断题答案只能是 A")));
    }

    @Test
    @DisplayName("合法选择题返回 200，并落库归一化后的字段")
    void createQuestion_valid_returns200() throws Exception {
        MvcResult result = postQuestion(tokenOf(teacher.getUsername()), validQuestion(bank.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        long id = idOf(result);
        Question saved = questionMapper.selectById(id);
        assertThat(saved).isNotNull();
        assertThat(saved.getType()).isZero();
        assertThat(saved.getContent()).isEqualTo("1+1=?");
        assertThat(saved.getAnswer()).isEqualTo("B");
        assertThat(saved.getScore()).isEqualTo(5);
        // 选项统一成库里存储的格式，便于判分与前端渲染
        assertThat(saved.getOptions()).isEqualTo(OPTIONS_AB);
        // 归属取当前登录用户，而不是请求体里可能带的值
        assertThat(saved.getCreatedBy()).isEqualTo(teacher.getId());
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("合法判断题：答案转大写、自动补「正确/错误」选项")
    void createQuestion_judge_autoFillsOptions() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("type", 2);
        body.remove("options");
        body.put("answer", "b");

        MvcResult result = postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isOk())
                .andReturn();

        Question saved = questionMapper.selectById(idOf(result));
        assertThat(saved.getAnswer()).isEqualTo("B");
        assertThat(saved.getOptions()).contains("正确").contains("错误");
    }

    @Test
    @DisplayName("填空题不要求选项，且 options 落库为 null")
    void createQuestion_blankTypeWithoutOptions_returns200() throws Exception {
        Map<String, Object> body = validQuestion(bank.getId());
        body.put("type", 3);
        body.remove("options");
        body.put("difficulty", null);
        body.put("answer", "H2O");

        MvcResult result = postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isOk())
                .andReturn();

        Question saved = questionMapper.selectById(idOf(result));
        assertThat(saved.getOptions()).isNull();
        assertThat(saved.getDifficulty()).as("难度留空按中等").isEqualTo(2);
    }

    @Test
    @DisplayName("bankId 指向不存在的题库返回 404（避免落出孤儿题）")
    void createQuestion_unknownBank_returns404() throws Exception {
        Map<String, Object> body = validQuestion(99999999L);

        postQuestion(tokenOf(teacher.getUsername()), body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 2. 归属校验 ====================

    @Test
    @DisplayName("教师只能改自己的题：改别人的题返回 403")
    void updateQuestion_ofOthers_returns403() throws Exception {
        User other = createUser("t2_" + unique(), "教师B", 1);
        Question mine = createQuestion(bank.getId(), 0, 1, "教师A的题", "A", 5);

        Map<String, Object> body = validQuestion(bank.getId());
        body.put("content", "被教师B改掉了");

        mockMvc.perform(put("/api/questions/" + mine.getId())
                        .header("Authorization", "Bearer " + tokenOf(other.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message", containsString("只能修改自己创建的题目")));

        assertThat(questionMapper.selectById(mine.getId()).getContent()).isEqualTo("教师A的题");
    }

    @Test
    @DisplayName("教师只能删自己的题：删别人的题返回 403")
    void deleteQuestion_ofOthers_returns403() throws Exception {
        User other = createUser("t2_" + unique(), "教师B", 1);
        Question mine = createQuestion(bank.getId(), 0, 1, "教师A的题", "A", 5);

        mockMvc.perform(delete("/api/questions/" + mine.getId())
                        .header("Authorization", "Bearer " + tokenOf(other.getUsername())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message", containsString("只能删除自己创建的题目")));

        assertThat(questionMapper.selectById(mine.getId())).isNotNull();
    }

    @Test
    @DisplayName("教师只能删自己的题库：删别人的库返回 403（即使库是空的）")
    void deleteBank_ofOthers_returns403() throws Exception {
        User other = createUser("t2_" + unique(), "教师B", 1);
        QuestionBank othersBank = createBank("教师B的题库_" + unique(), other.getId());

        mockMvc.perform(delete("/api/banks/" + othersBank.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message", containsString("只能删除自己创建的题库")));

        assertThat(bankMapper.selectById(othersBank.getId())).isNotNull();
    }

    @Test
    @DisplayName("本人改 / 删自己的题都成功")
    void updateAndDelete_ownQuestion_succeeds() throws Exception {
        Question mine = createQuestion(bank.getId(), 0, 1, "自己的题", "A", 5);
        String token = tokenOf(teacher.getUsername());

        Map<String, Object> body = validQuestion(bank.getId());
        body.put("content", "改过的题干");
        body.put("answer", "A");

        mockMvc.perform(put("/api/questions/" + mine.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("改过的题干"));

        mockMvc.perform(delete("/api/questions/" + mine.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(questionMapper.selectById(mine.getId())).isNull();
    }

    @Test
    @DisplayName("更新只写白名单字段：createdBy / createdAt / id 不可被篡改")
    void updateQuestion_cannotTamperOwnershipOrTimestamps() throws Exception {
        User other = createUser("t2_" + unique(), "教师B", 1);
        Question mine = createQuestion(bank.getId(), 0, 1, "原始题干", "A", 5);
        LocalDateTime createdAtBefore = questionMapper.selectById(mine.getId()).getCreatedAt();

        Map<String, Object> body = validQuestion(bank.getId());
        body.put("content", "新题干");
        body.put("answer", "A");
        // 攻击面：客户端试图把题「洗」到自己名下并伪造创建时间
        body.put("id", 99999999L);
        body.put("createdBy", other.getId());
        body.put("createdAt", "2000-01-01T00:00:00");
        body.put("updatedAt", "2000-01-01T00:00:00");

        mockMvc.perform(put("/api/questions/" + mine.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("新题干"));

        Question after = questionMapper.selectById(mine.getId());
        assertThat(after.getCreatedBy()).as("归属不能被请求体改写").isEqualTo(teacher.getId());
        assertThat(after.getCreatedAt()).as("创建时间不能被请求体改写").isEqualTo(createdAtBefore);
        assertThat(questionMapper.selectById(99999999L)).as("id 不能被请求体改写").isNull();
    }

    @Test
    @DisplayName("选择题改成简答题时旧选项被清空（null 也要落库，否则会留下「带选项的简答题」）")
    void updateQuestion_toSubjective_clearsOptions() throws Exception {
        Question choice = createQuestion(bank.getId(), 0, 1, "选择题", "A", 5);
        assertThat(questionMapper.selectById(choice.getId()).getOptions()).isNotNull();

        Map<String, Object> body = validQuestion(bank.getId());
        body.put("type", 4);
        body.remove("options");
        body.put("content", "改为简答题");
        body.put("answer", "参考答案");

        mockMvc.perform(put("/api/questions/" + choice.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());

        Question after = questionMapper.selectById(choice.getId());
        assertThat(after.getType()).isEqualTo(4);
        assertThat(after.getOptions()).as("旧的 A/B/C/D 选项必须被清掉").isNull();
    }

    @Test
    @DisplayName("ADMIN 可以改 / 删其他教师创建的题目与题库")
    void admin_canOperateOnOthersData() throws Exception {
        User admin = createUser("admin_" + unique(), "管理员", 0);
        String adminToken = tokenOf(admin.getUsername());
        Question othersQuestion = createQuestion(bank.getId(), 0, 1, "教师的题", "A", 5);

        Map<String, Object> body = validQuestion(bank.getId());
        body.put("content", "管理员改过的题干");
        body.put("answer", "A");

        mockMvc.perform(put("/api/questions/" + othersQuestion.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("管理员改过的题干"));

        mockMvc.perform(delete("/api/questions/" + othersQuestion.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 教师离职后存量题库要有人能接管，否则数据永久卡死
        QuestionBank othersBank = createBank("待接管的空题库_" + unique(), teacher.getId());
        mockMvc.perform(delete("/api/banks/" + othersBank.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertThat(bankMapper.selectById(othersBank.getId())).isNull();
    }

    @Test
    @DisplayName("读操作不按创建人过滤：教师能看到同事建的题库与题目（共享视图是有意的取舍）")
    void listQuestions_includesOtherTeachersData() throws Exception {
        User other = createUser("t2_" + unique(), "教师B", 1);
        QuestionBank othersBank = createBank("教师B的题库_" + unique(), other.getId());
        Question othersQuestion = createQuestion(othersBank.getId(), 0, 1, "教师B的题", "A", 5);

        mockMvc.perform(get("/api/questions/" + othersQuestion.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("教师B的题"));

        MvcResult banks = mockMvc.perform(get("/api/banks")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper
                .readTree(banks.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        boolean visible = false;
        for (JsonNode b : data) {
            if (b.path("id").asLong() == othersBank.getId()) {
                visible = true;
            }
        }
        assertThat(visible).as("共享题库视图：同事建的库也要能查到").isTrue();
    }

    // ==================== 3. 题库创建 ====================

    @Test
    @DisplayName("题库名为空返回 400")
    void createBank_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/banks")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"subject\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("建题库时归属取当前登录用户，请求体里的 createdBy 被忽略")
    void createBank_usesCurrentUserAsCreator() throws Exception {
        User other = createUser("t2_" + unique(), "教师B", 1);

        MvcResult result = mockMvc.perform(post("/api/banks")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新题库_" + unique() + "\",\"subject\":\"Java\","
                                + "\"createdBy\":" + other.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        assertThat(bankMapper.selectById(id).getCreatedBy()).isEqualTo(teacher.getId());
    }

    // ==================== 4. 删除保护：试卷快照引用 ====================

    @Test
    @DisplayName("题目被试卷快照引用时删除返回 400，且消息里带上试卷标题")
    void deleteQuestion_referencedBySnapshot_returns400() throws Exception {
        Question referenced = createQuestion(bank.getId(), 0, 1, "被引用的题", "A", 5);
        String examTitle = "四川专升本 · 高等数学模拟卷（一）";
        insertExam(examTitle, "[{\"questionId\":" + referenced.getId() + ",\"score\":5}]");

        mockMvc.perform(delete("/api/questions/" + referenced.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString(examTitle)))
                .andExpect(jsonPath("$.message", containsString("不能删除")));

        // 拦住了删除：快照里的引用不会变成悬空 ID
        assertThat(questionMapper.selectById(referenced.getId())).isNotNull();
    }

    @Test
    @DisplayName("未被任何试卷引用的题目可以正常删除")
    void deleteQuestion_notReferenced_succeeds() throws Exception {
        Question free = createQuestion(bank.getId(), 0, 1, "没人引用的题", "A", 5);
        // 另有一份试卷，但引的是别的题：不能因为「存在试卷」就误拦
        Question other = createQuestion(bank.getId(), 0, 1, "卷子上真正考的题", "A", 5);
        insertExam("无关的卷子_" + unique(),
                "[{\"questionId\":" + other.getId() + ",\"score\":5}]");

        mockMvc.perform(delete("/api/questions/" + free.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk());

        assertThat(questionMapper.selectById(free.getId())).isNull();
    }

    @Test
    @DisplayName("多份试卷引用同一题时，消息给出被引用的份数")
    void deleteQuestion_referencedTwice_reportsCount() throws Exception {
        Question referenced = createQuestion(bank.getId(), 0, 1, "被两份卷子引用的题", "A", 5);
        String snapshot = "[{\"questionId\":" + referenced.getId() + ",\"score\":5}]";
        insertExam("卷甲_" + unique(), snapshot);
        insertExam("卷乙_" + unique(), snapshot);

        mockMvc.perform(delete("/api/questions/" + referenced.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("2 份试卷引用")));
    }

    // ==================== 辅助 ====================

    /** 一道合法单选题的请求体（各用例按需增删字段） */
    private Map<String, Object> validQuestion(Long bankId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bankId", bankId);
        m.put("type", 0);
        m.put("difficulty", 1);
        m.put("content", "1+1=?");
        m.put("options", OPTIONS_AB);
        m.put("answer", "B");
        m.put("score", 5);
        m.put("analysis", "常识");
        m.put("status", 1);
        return m;
    }

    /** 提交一道题的请求体；状态码由各用例自行断言 */
    private org.springframework.test.web.servlet.ResultActions postQuestion(String token,
                                                                           Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(post("/api/questions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    /** 直接落一份「题面固定」的试卷，用于制造快照引用 */
    private Exam insertExam(String title, String snapshotJson) {
        Exam exam = new Exam();
        exam.setTitle(title);
        exam.setBankId(bank.getId());
        exam.setDuration(60);
        exam.setTotalScore(5);
        exam.setPassScore(3);
        exam.setStatus(0);
        exam.setCreatedBy(teacher.getId());
        exam.setQuestionSnapshot(snapshotJson);
        examMapper.insert(exam);
        return examMapper.selectById(exam.getId());
    }

    /** 走真实登录换 JWT：与浏览器里跑的链路一致 */
    private String tokenOf(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        // 必须显式指定 UTF-8：MockHttpServletResponse 默认按 ISO-8859-1 解码
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asText();
    }
}