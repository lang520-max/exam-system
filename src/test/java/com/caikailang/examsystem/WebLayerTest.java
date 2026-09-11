package com.caikailang.examsystem;

import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.mapper.UserMapper;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层测试：Thymeleaf 页面能否渲染、REST 接口是否返回统一结构、
 * 未登录与越权是否被正确拦截。
 *
 * <p>这些用例专门挡住两类最容易悄悄回归的问题：<b>模板改坏了</b> 与
 * <b>新接口忘了标注解导致鉴权失效</b>。</p>
 *
 * @author 蔡开浪
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebLayerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserMapper userMapper;

    /** 取 H2 演示数据里的账号，构造一个「已登录」的 Session */
    private MockHttpSession sessionFor(String username) {
        User user = userMapper.findByUsername(username);
        assertNotNull(user, "演示数据里应当存在账号 " + username);
        user.clearPassword();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthInterceptor.SESSION_USER, user);
        return session;
    }

    private String uniqueUsername() {
        return "webu" + Math.abs(System.nanoTime() % 100000000L);
    }

    /* ================================================================== */
    /* 页面渲染                                                            */
    /* ================================================================== */

    @Test
    @DisplayName("页面：登录页与注册页可正常渲染，并给出演示账号提示")
    void authPages_shouldRender() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("在线考试")))
            .andExpect(content().string(containsString("演示账号")))
            .andExpect(content().string(containsString("teacher / 123456")));

        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("注册")));
    }

    @Test
    @DisplayName("页面：未登录访问首页展示落地页")
    void indexPage_shouldRenderForAnonymous() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("在线考试 / 题库系统")))
            .andExpect(content().string(containsString("立即登录")));
    }

    @Test
    @DisplayName("页面：教师题库页渲染出题干、选项与分页（Thymeleaf 数据绑定正常）")
    void teacherQuestionsPage_shouldRenderQuestionBank() throws Exception {
        mockMvc.perform(get("/teacher/questions").session(sessionFor("teacher")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("题库管理")))
            .andExpect(content().string(containsString("下列哪个关键字用于声明一个不可被继承的类？")))
            .andExpect(content().string(containsString("王老师")))
            .andExpect(content().string(containsString("单选题")));
    }

    @Test
    @DisplayName("页面：学生端考试列表渲染出已发布试卷与该生已交卷成绩")
    void studentExamsPage_shouldRenderPublishedPapers() throws Exception {
        mockMvc.perform(get("/student/exams").session(sessionFor("student02")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("可参加的考试")))
            .andExpect(content().string(containsString("《Java程序设计》期中测试")))
            // student02 在演示数据里已经交过卷（80 分），列表应显示已交卷状态
            .andExpect(content().string(containsString("已交卷")))
            .andExpect(content().string(containsString("80.0")));
    }

    @Test
    @DisplayName("页面：学生成绩页渲染出个人统计与成绩列表")
    void studentScoresPage_shouldRenderStats() throws Exception {
        mockMvc.perform(get("/student/scores").session(sessionFor("student02")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("我的成绩")))
            .andExpect(content().string(containsString("平均分")))
            .andExpect(content().string(containsString("80.0")));
    }

    @Test
    @DisplayName("页面：教师成绩统计页渲染出成绩列表与平均分/及格率")
    void teacherScoresPage_shouldRenderStats() throws Exception {
        mockMvc.perform(get("/teacher/scores").param("paperId", "1").session(sessionFor("teacher")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("成绩统计")))
            .andExpect(content().string(containsString("及格率")))
            .andExpect(content().string(containsString("张小红")))
            .andExpect(content().string(containsString("80.0")));
    }

    @Test
    @DisplayName("页面：学生错题回顾页渲染出题目、学生作答与正确答案")
    void reviewPage_shouldRenderWrongAnswers() throws Exception {
        mockMvc.perform(get("/student/review").param("recordId", "1").session(sessionFor("student02")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("错题回顾")))
            .andExpect(content().string(containsString("正确答案")))
            .andExpect(content().string(containsString("Java 中一个类可以同时继承多个父类。")));
    }

    /* ================================================================== */
    /* 鉴权：未登录 401 / 页面跳登录页                                       */
    /* ================================================================== */

    @Test
    @DisplayName("鉴权：未登录调用题库写接口返回 401 与统一 Result 结构")
    void questionApi_shouldRejectAnonymousWith401() throws Exception {
        mockMvc.perform(post("/api/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SINGLE\",\"subject\":\"x\",\"content\":\"x\",\"optionA\":\"a\",\"optionB\":\"b\",\"answer\":\"A\",\"score\":5}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").value("请先登录后再操作"));
    }

    @Test
    @DisplayName("鉴权：未登录调用成绩接口与考试接口都被拦截")
    void protectedApis_shouldRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/scores/my"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/exams"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/exams/submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("鉴权：未登录访问受保护页面重定向到登录页并带上回跳地址")
    void protectedPages_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/teacher/questions"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?redirect=/teacher/questions"));

        mockMvc.perform(get("/student/exams"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?redirect=/student/exams"));
    }

    @Test
    @DisplayName("越权：学生调用教师专属接口返回 403，访问教师页面被重定向")
    void teacherOnlyEndpoints_shouldRejectStudent() throws Exception {
        MockHttpSession student = sessionFor("student01");

        mockMvc.perform(post("/api/questions")
                .session(student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SINGLE\",\"subject\":\"x\",\"content\":\"x\",\"optionA\":\"a\",\"optionB\":\"b\",\"answer\":\"A\",\"score\":5}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/questions").session(student))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/scores/paper/1/stats").session(student))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        // 学生访问教师页面：页面请求不带 Accept: application/json，应当跳登录页而不是返回 JSON
        mockMvc.perform(get("/teacher/questions").session(student))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("鉴权：登出后原 Session 失效，再次访问受保护接口返回 401")
    void logout_shouldInvalidateSession() throws Exception {
        MockHttpSession session = sessionFor("student01");

        mockMvc.perform(get("/api/scores/my").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/scores/my").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    /* ================================================================== */
    /* 参数校验与 REST 全流程                                               */
    /* ================================================================== */

    @Test
    @DisplayName("校验：注册表单参数不合法时返回第一条校验提示，且不落库")
    void register_shouldReturnFirstValidationMessage() throws Exception {
        // 只违反「用户名长度 4~20」一条约束，保证拿到的提示是确定的
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"password\":\"123456\",\"nickname\":\"正常昵称\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("用户名长度需在 4~20 位之间"));
    }

    @Test
    @DisplayName("校验：重复用户名注册被拒绝，密码错误登录返回 401")
    void loginAndRegister_shouldValidateBusinessRules() throws Exception {
        String username = uniqueUsername();
        String body = "{\"username\":\"" + username + "\",\"password\":\"123456\",\"nickname\":\"网页学生\"}";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").isNumber())
            // 返回的用户对象不能带密码字段（Jackson 序列化 null 时该字段值为 null）
            .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value(containsString("已存在")));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("REST 全流程：注册→登录→看可考列表→开考→取题→交卷→查成绩")
    void fullRestExamFlow_shouldWork() throws Exception {
        String username = uniqueUsername();

        // 1) 注册
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"123456\",\"nickname\":\"流程测试生\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // 2) 登录，拿到会话
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.nickname").value("流程测试生"))
            .andExpect(jsonPath("$.data.role").value("STUDENT"))
            .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session, "登录后应当写入 Session");

        // 3) 可参加的考试列表，取出演示试卷 ID
        String listBody = mockMvc.perform(get("/api/exams").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.list").isArray())
            .andReturn().getResponse().getContentAsString();
        JsonNode papers = JSON.readTree(listBody).path("data").path("list");
        assertTrue(papers.size() >= 1, "演示数据里应当至少有一份已发布试卷");
        long paperId = papers.get(0).path("id").asLong();

        // 4) 开考
        String startBody = mockMvc.perform(post("/api/exams/" + paperId + "/start").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("ONGOING"))
            .andReturn().getResponse().getContentAsString();
        long recordId = JSON.readTree(startBody).path("data").path("id").asLong();

        // 5) 取题：返回统一结构，且绝不能带正确答案
        String questionsBody = mockMvc.perform(get("/api/exams/records/" + recordId + "/questions").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].sortOrder").value(1))
            .andReturn().getResponse().getContentAsString();
        assertFalse(questionsBody.contains("\"answer\""),
            "考试进行中下发的题目不能包含正确答案字段");

        // 6) 交卷（演示试卷 8 道题的正确答案依次是 A B C D ABC ABD B A，全对应得 100 分）
        String submitBody = "{\"recordId\":" + recordId + ",\"answers\":{"
            + "\"1\":\"A\",\"2\":\"B\",\"3\":\"C\",\"4\":\"D\","
            + "\"5\":\"ABC\",\"6\":\"ABD\",\"7\":\"B\",\"8\":\"A\"}}";
        mockMvc.perform(post("/api/exams/submit")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.data.score").value(100.0))
            .andExpect(jsonPath("$.data.correctCount").value(8))
            .andExpect(jsonPath("$.data.timeout").value(false));

        // 7) 重复交卷被拒绝
        mockMvc.perform(post("/api/exams/submit")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value(containsString("请勿重复提交")));

        // 8) 查成绩：能看到自己这场考试
        String myScores = mockMvc.perform(get("/api/scores/my").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn().getResponse().getContentAsString();
        assertEquals(1, JSON.readTree(myScores).path("data").path("total").asInt(),
            "该学生只有这一场考试记录");
        assertEquals("SUBMITTED",
            JSON.readTree(myScores).path("data").path("list").get(0).path("status").asText());

        // 9) 成绩单与错题回顾（全对，错题为空）
        mockMvc.perform(get("/api/exams/records/" + recordId + "/review").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(8));
        mockMvc.perform(get("/api/exams/records/" + recordId + "/wrong").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        // 10) 个人成绩统计
        mockMvc.perform(get("/api/scores/my/stats").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.passCount").value(1))
            .andExpect(jsonPath("$.data.passRate").value(100.0));
    }

    @Test
    @DisplayName("REST：教师端组卷与成绩统计接口返回统一结构")
    void teacherApis_shouldReturnWrappedResult() throws Exception {
        MockHttpSession teacher = sessionFor("teacher");

        mockMvc.perform(get("/api/questions").param("pageSize", "5").session(teacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.message").value("success"))
            .andExpect(jsonPath("$.data.list").isArray())
            .andExpect(jsonPath("$.data.pageSize").value(5));

        mockMvc.perform(get("/api/questions/subjects").session(teacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("Java程序设计"));

        mockMvc.perform(get("/api/papers/1/questions").session(teacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(8));

        mockMvc.perform(get("/api/scores/paper/1/stats").session(teacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.maxScore").value(80.0))
            .andExpect(jsonPath("$.data.minScore").value(80.0))
            .andExpect(jsonPath("$.data.passRate").value(100.0));

        // 不存在的试卷：返回 404 而不是 500
        mockMvc.perform(get("/api/papers/999999").session(teacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }
}
