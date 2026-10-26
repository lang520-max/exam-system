package com.exam.web;

import com.exam.BaseIntegrationTest;
import com.exam.common.constant.QuestionType;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.exam.service.ExamRuleService;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web 层测试 —— 真实走 Spring Security 过滤器链 + JWT 鉴权 + REST 接口。
 *
 * <p>这里<b>不用</b> {@code @WithMockUser}：本项目控制器通过
 * {@code @AuthenticationPrincipal JwtUser} 取身份，而 MockUser 的 principal
 * 是 Spring 自带的 User 对象，注入进来会是 null，测出来的东西与线上不一致。
 * 因此改为先用真实 {@code /api/auth/login} 换一个 JWT，
 * 再把它放进 {@code Authorization} 头——这条链路和浏览器里跑的完全一样。</p>
 */
@AutoConfigureMockMvc
@DisplayName("Web 层 · REST 接口与鉴权")
class WebLayerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamMapper examMapper;

    private static final String PASSWORD = "123456";

    // ---------- 认证 ----------

    @Test
    @DisplayName("登录成功返回双 Token 与角色")
    void login_withValidCredentials_returnsTokens() throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(teacher.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("密码错误返回 401")
    void login_withWrongPassword_returns401() throws Exception {
        String body = """
                {"username":"%s","password":"wrong-password"}
                """.formatted(teacher.getUsername());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("用户名不存在与密码错误返回同一句提示（防账号枚举）")
    void login_unknownUser_returnsSameMessage() throws Exception {
        MvcResult unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no_such_user\",\"password\":\"123456\"}"))
                .andReturn();
        MvcResult wrongPwd = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"wrong-password\"}"
                                .formatted(teacher.getUsername())))
                .andReturn();

        String msg1 = objectMapper.readTree(unknown.getResponse().getContentAsString()).get("message").asText();
        String msg2 = objectMapper.readTree(wrongPwd.getResponse().getContentAsString()).get("message").asText();
        assertThat(msg1).isEqualTo(msg2);
    }

    @Test
    @DisplayName("注册接口只创建学生，不接受前端传角色")
    void register_ignoresClientSuppliedRole() throws Exception {
        String username = "reg_" + unique();
        String body = """
                {"username":"%s","password":"%s","realName":"新同学","role":0}
                """.formatted(username, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 密码哈希不出网：与 /api/auth/me 的处理一致
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("role").asInt()).as("注册接口必须强制学生角色").isEqualTo(2);
    }

    @Test
    @DisplayName("注册重复用户名被拒绝")
    void register_duplicateUsername_rejected() throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(teacher.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册参数不合法时返回校验提示")
    void register_invalidBody_returnsValidationMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"1\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("登出接口可用且幂等：带 Refresh Token 与不带都返回成功")
    void logout_isAvailableAndIdempotent() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(teacher.getUsername(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = objectMapper
                .readTree(loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout").header("X-Refresh-Token", refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 幂等：没带 token（例如本地已经清干净了）也应当成功，否则前端会卡在登出失败
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("带合法 Token 可读取当前用户信息")
    void me_withValidToken_returnsUser() throws Exception {
        String token = login(teacher.getUsername());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(teacher.getUsername()))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("密码错误返回 HTTP 401（而不是 200 里塞业务码）")
    void login_withWrongPassword_returnsHttp401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"wrong-password\"}"
                                .formatted(teacher.getUsername())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("重复交卷返回 HTTP 409 Conflict")
    void submit_duplicate_returns409() throws Exception {
        Exam exam = buildPublishedExam();
        String studentToken = login(student.getUsername());

        MvcResult startResult = mockMvc.perform(
                        post("/api/exams/" + exam.getId() + "/start")
                                .header("Authorization", "Bearer " + studentToken))
                .andReturn();
        long recordId = objectMapper.readTree(startResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String body = "{\"recordId\":" + recordId + ",\"answers\":{}}";

        // 第一次交卷成功
        mockMvc.perform(post("/api/exams/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", "dup-first-" + unique())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // 第二次交卷：状态已不是 ONGOING，应返回 409
        mockMvc.perform(post("/api/exams/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", "dup-second-" + unique())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("Refresh Token 可以换取新的 Access Token")
    void refresh_returnsNewAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(teacher.getUsername(), PASSWORD)))
                .andReturn();
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh").header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // ---------- 鉴权 ----------

    @Test
    @DisplayName("未带 Token 访问受保护接口返回 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/exams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Token 被篡改时返回 401")
    void protectedEndpoint_withTamperedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("学生调用教师专属接口返回 403")
    void teacherEndpoint_asStudent_returns403() throws Exception {
        String token = login(student.getUsername());

        mockMvc.perform(get("/api/questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("学生不能创建题库")
    void createBank_asStudent_returns403() throws Exception {
        String token = login(student.getUsername());

        mockMvc.perform(post("/api/banks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"非法题库\",\"subject\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教师可以创建题库")
    void createBank_asTeacher_succeeds() throws Exception {
        String token = login(teacher.getUsername());

        mockMvc.perform(post("/api/banks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HTTP 题库 " + unique() + "\",\"subject\":\"HTTP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    @DisplayName("学生可以读取考试列表")
    void listExams_asStudent_succeeds() throws Exception {
        String token = login(student.getUsername());

        mockMvc.perform(get("/api/exams").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ---------- REST 全流程 ----------

    @Test
    @DisplayName("REST 全流程：登录 → 开考 → 取题 → 交卷 → 查成绩")
    void fullRestFlow_overHttp() throws Exception {
        Exam exam = buildPublishedExam();

        String studentToken = login(student.getUsername());

        // 1) 开考
        MvcResult startResult = mockMvc.perform(
                        post("/api/exams/" + exam.getId() + "/start")
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        long recordId = objectMapper.readTree(startResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 2) 取题：断言响应里不出现标准答案字段
        MvcResult questionsResult = mockMvc.perform(
                        get("/api/exams/records/" + recordId + "/questions")
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        String questionsJson = questionsResult.getResponse().getContentAsString();
        assertThat(questionsJson).as("考试中下发的题目不能带标准答案").doesNotContain("\"answer\"");

        JsonNode questions = objectMapper.readTree(questionsJson).get("data");

        // 3) 按题目 ID 组装全对答案
        Map<String, String> answers = new java.util.LinkedHashMap<>();
        for (JsonNode q : questions) {
            long qid = q.get("id").asLong();
            Question entity = questionMapper.selectById(qid);
            answers.put(String.valueOf(qid), entity.getAnswer());
        }
        String submitBody = objectMapper.writeValueAsString(
                Map.of("recordId", recordId, "answers", answers));

        mockMvc.perform(post("/api/exams/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .header("X-Request-Id", "rest-flow-" + unique())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 4) 查成绩：全部答对应为满分并通过
        mockMvc.perform(get("/api/exams/records/" + recordId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(exam.getTotalScore()))
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    @DisplayName("交卷缺少 X-Request-Id 请求头时被拒绝")
    void submit_withoutRequestIdHeader_rejected() throws Exception {
        Exam exam = buildPublishedExam();
        String studentToken = login(student.getUsername());

        MvcResult startResult = mockMvc.perform(
                        post("/api/exams/" + exam.getId() + "/start")
                                .header("Authorization", "Bearer " + studentToken))
                .andReturn();
        long recordId = objectMapper.readTree(startResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/exams/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordId\":" + recordId + ",\"answers\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("学生不能查看他人的答卷")
    void getResult_ofOthersRecord_returns403() throws Exception {
        Exam exam = buildPublishedExam();
        String studentToken = login(student.getUsername());

        MvcResult startResult = mockMvc.perform(
                        post("/api/exams/" + exam.getId() + "/start")
                                .header("Authorization", "Bearer " + studentToken))
                .andReturn();
        long recordId = objectMapper.readTree(startResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        User other = createUser("other_" + unique(), "别人", 2);
        String otherToken = login(other.getUsername());

        mockMvc.perform(get("/api/exams/records/" + recordId + "/result")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("教师可以查看考试成绩统计")
    void stats_asTeacher_succeeds() throws Exception {
        Exam exam = buildPublishedExam();
        String teacherToken = login(teacher.getUsername());

        mockMvc.perform(get("/api/exams/" + exam.getId() + "/stats")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examTitle").value(exam.getTitle()))
                .andExpect(jsonPath("$.data.submittedCount").value(0));
    }

    @Test
    @DisplayName("不存在的考试返回 404 业务码")
    void getExam_notFound_returns404Code() throws Exception {
        String token = login(teacher.getUsername());

        mockMvc.perform(get("/api/exams/99999999").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- 静态资源放行 ----------

    @Test
    @DisplayName("KaTeX 静态资源无需鉴权即可访问")
    void vendorAssets_arePublic() throws Exception {
        // 刻意不带 Authorization 头：浏览器加载 <link>/<script> 时无法附加请求头，
        // 所以 /vendor/** 一旦落到 anyRequest().authenticated() 就会 401，
        // 表现为「公式一个都不显示，但页面本身没有报错」——很难排查。
        mockMvc.perform(get("/vendor/katex/katex.min.js"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("公式渲染脚本 math.js 无需鉴权即可访问")
    void mathScript_isPublic() throws Exception {
        mockMvc.perform(get("/js/math.js"))
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    /** 用真实登录接口换一个 JWT */
    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    /** 造一份「已发布」的单题试卷 */
    private Exam buildPublishedExam() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "HTTP 单选", "A", 10);
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("HTTP 试卷_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(5);
        ExamGenerateDTO.RuleItem rule = new ExamGenerateDTO.RuleItem();
        rule.setType(QuestionType.SINGLE);
        rule.setCount(1);
        rule.setScore(10);
        form.setRules(List.of(rule));

        Exam exam = examRuleService.generateExam(form, teacher.getId());
        exam.setStatus(1);
        examMapper.updateById(exam);
        return examMapper.selectById(exam.getId());
    }
}