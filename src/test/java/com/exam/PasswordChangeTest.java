package com.exam;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自助修改密码。
 *
 * <p>以前登录页只有「忘记密码请联系教务」——密码泄露了也只能等人工处理。
 * 这里锁住四件事：必须是登录用户、原密码必须对、新密码不能与原密码相同、
 * **改完之后旧密码立刻不能登录、新密码可以**。</p>
 */
@AutoConfigureMockMvc
@DisplayName("自助修改密码")
class PasswordChangeTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String OLD = "123456";

    @Test
    @DisplayName("未登录不能改密码（401）")
    void requiresLogin() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + OLD + "\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("原密码不对要拒绝，且不能改动密码")
    void wrongOldPasswordRejected() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + login(student.getUsername(), OLD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong-old\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("原密码不正确"));

        assertThat(loginStatus(student.getUsername(), OLD)).as("原密码仍然可用").isEqualTo(200);
    }

    @Test
    @DisplayName("新密码不能与原密码相同")
    void sameAsOldRejected() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + login(student.getUsername(), OLD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + OLD + "\",\"newPassword\":\"" + OLD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能与原密码相同"));
    }

    @Test
    @DisplayName("改成功后：旧密码失效、新密码可登录")
    void changedPasswordTakesEffect() throws Exception {
        String newPwd = "newpass123";
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + login(student.getUsername(), OLD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + OLD + "\",\"newPassword\":\"" + newPwd + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(loginStatus(student.getUsername(), OLD)).as("旧密码必须失效").isEqualTo(401);
        assertThat(loginStatus(student.getUsername(), newPwd)).as("新密码必须可用").isEqualTo(200);
    }

    @Test
    @DisplayName("新密码长度不足要拒绝（与注册同一套规则）")
    void tooShortPasswordRejected() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + login(student.getUsername(), OLD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + OLD + "\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    /** 用给定口令登录，返回 HTTP 状态码（200=成功，401=口令不对） */
    private int loginStatus(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        return res.getResponse().getStatus();
    }

    private String login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return node.get("data").get("accessToken").asText();
    }
}