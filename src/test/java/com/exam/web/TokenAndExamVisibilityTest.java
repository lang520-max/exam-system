package com.exam.web;

import com.exam.BaseIntegrationTest;
import com.exam.common.constant.QuestionType;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.entity.Exam;
import com.exam.mapper.ExamMapper;
import com.exam.service.ExamRuleService;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 走真实 Spring Security 过滤器链，覆盖两处越权/可见性缺陷：
 *
 * <ol>
 *   <li><b>Refresh Token 与 Access Token 不可区分</b>：
 *       原本任何一个 30 分钟的 Access Token 都能换出一整套新 Token（含 7 天的 Refresh Token）。</li>
 *   <li><b>试卷信息越权可见</b>：学生原本能从 {@code GET /api/exams/{id}} 拿到
 *       {@code questionSnapshot}（卷面考哪几题、各多少分）与 {@code ruleConfig}，
 *       也能从列表里读到未发布的草稿试卷。</li>
 * </ol>
 */
@AutoConfigureMockMvc
@DisplayName("Token 类型 与 试卷可见性")
class TokenAndExamVisibilityTest extends BaseIntegrationTest {

    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamMapper examMapper;

    private Exam draft;
    private Exam published;

    @BeforeEach
    void initExams() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "可见性题", "A", 10);
        draft = buildExam("草稿试卷_" + unique(), 0);
        published = buildExam("已发布试卷_" + unique(), 1);
    }

    private Exam buildExam(String title, int status) {
        ExamGenerateDTO.RuleItem rule = new ExamGenerateDTO.RuleItem();
        rule.setType(QuestionType.SINGLE);
        rule.setCount(1);
        rule.setScore(10);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle(title);
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(6);
        form.setRules(List.of(rule));

        Exam created = examRuleService.generateExam(form, teacher.getId());
        created.setStatus(status);
        examMapper.updateById(created);
        return examMapper.selectById(created.getId());
    }

    /**
     * 把响应体解析成 JsonNode。
     *
     * <p><b>必须显式指定 UTF-8</b>：{@code MockHttpServletResponse.getContentAsString()}
     * 在响应头没有声明 charset 时按 ISO-8859-1 解码（Spring Boot 3 的默认行为），
     * 中文会变成 {@code "å·²ååè¯å·_1"} 这种乱码，
     * 于是「用中文标题做断言」会莫名其妙地失败——而接口本身完全正常。
     * 既有测试没踩到，是因为它们只断言 token、id 这类纯 ASCII 字段。</p>
     */
    private JsonNode jsonOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 登录并返回响应里的 data 节点（含 accessToken / refreshToken） */
    private JsonNode loginData(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonOf(result).get("data");
    }

    private String tokenOf(String username) throws Exception {
        return loginData(username).get("accessToken").asText();
    }

    // ---------- Token 类型 ----------

    @Test
    @DisplayName("【关键】Access Token 不能当 Refresh Token 用")
    void refresh_withAccessToken_rejected() throws Exception {
        String accessToken = loginData(teacher.getUsername()).get("accessToken").asText();

        mockMvc.perform(post("/api/auth/refresh").header("X-Refresh-Token", accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("【关键】Refresh Token 不能当 Access Token 用：受保护接口必须 401")
    void protectedEndpoint_withRefreshToken_rejected() throws Exception {
        String refreshToken = loginData(teacher.getUsername()).get("refreshToken").asText();

        // 上一轮只堵了反方向（access 换 refresh）。这个方向此前一直敞着：
        // refresh 有 7 天有效期，若能放进 Authorization 头调业务接口，
        // 「access 短命 30 分钟以限制泄露窗口」这个设计前提就被绕过了一半。
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        // 同一把 token 在它该去的接口上依然有效（别把功能一起修坏了）
        mockMvc.perform(post("/api/auth/refresh").header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Refresh Token 仍能正常换取新 Token")
    void refresh_withRefreshToken_succeeds() throws Exception {
        String refreshToken = loginData(teacher.getUsername()).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh").header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Access Token 仍能正常访问受保护接口（加类型声明不影响鉴权）")
    void accessToken_stillWorksForProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenOf(student.getUsername())))
                .andExpect(status().isOk());
    }

    // ---------- 试卷可见性 ----------

    @Test
    @DisplayName("【关键】学生读试卷详情拿不到题目快照与组卷规则")
    void getExam_asStudent_hidesSnapshotAndRules() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/exams/" + published.getId())
                        .header("Authorization", "Bearer " + tokenOf(student.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = jsonOf(result).get("data");

        assertThat(data.has("questionSnapshot")).isFalse();
        assertThat(data.has("ruleConfig")).isFalse();
        // 其余字段仍在，前端展示不受影响
        assertThat(data.get("title").asText()).isEqualTo(published.getTitle());
        assertThat(data.get("duration").asInt()).isEqualTo(30);
    }

    @Test
    @DisplayName("教师读试卷详情仍能拿到快照与规则")
    void getExam_asTeacher_keepsSnapshotAndRules() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/exams/" + published.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = jsonOf(result).get("data");

        assertThat(data.has("questionSnapshot")).isTrue();
        assertThat(data.get("questionSnapshot").asText()).isNotBlank();
        assertThat(data.has("ruleConfig")).isTrue();
    }

    @Test
    @DisplayName("学生读未发布（草稿）试卷详情返回 404")
    void getDraftExam_asStudent_returns404() throws Exception {
        mockMvc.perform(get("/api/exams/" + draft.getId())
                        .header("Authorization", "Bearer " + tokenOf(student.getUsername())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("教师可以读草稿试卷详情")
    void getDraftExam_asTeacher_succeeds() throws Exception {
        mockMvc.perform(get("/api/exams/" + draft.getId())
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生查考试列表看不到草稿试卷，但能看到已发布的")
    void listExams_asStudent_excludesDraft() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/exams")
                        .header("Authorization", "Bearer " + tokenOf(student.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        List<String> titles = jsonOf(result).get("data").findValuesAsText("title");

        assertThat(titles).doesNotContain(draft.getTitle());
        assertThat(titles).contains(published.getTitle());
    }

    @Test
    @DisplayName("学生显式请求 status=0 也只拿到空列表")
    void listExams_asStudent_status0_returnsEmpty() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/exams?status=0")
                        .header("Authorization", "Bearer " + tokenOf(student.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonOf(result).get("data")).isEmpty();
    }

    @Test
    @DisplayName("教师列表能看到草稿试卷")
    void listExams_asTeacher_includesDraft() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/exams")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonOf(result).get("data").findValuesAsText("title")).contains(draft.getTitle());
    }

    @Test
    @DisplayName("列表接口任何角色都拿不到题目快照")
    void listExams_neverLeaksSnapshot() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/exams")
                        .header("Authorization", "Bearer " + tokenOf(teacher.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonOf(result).get("data").findValues("questionSnapshot")).isEmpty();
    }
}