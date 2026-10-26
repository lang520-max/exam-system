package com.exam.common;

import com.exam.common.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 工具类单元测试 —— 不启动 Spring。
 *
 * <p>覆盖双 Token 的签发与解析、过期与篡改的拒绝。</p>
 */
@DisplayName("JWT 双 Token")
class JwtUtilTest {

    private static final String SECRET = "unit-test-jwt-secret-key-must-be-at-least-256-bits-long-abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // access 30 分钟，refresh 7 天
        jwtUtil = new JwtUtil(SECRET, 1_800_000L, 604_800_000L);
    }

    @Test
    @DisplayName("签发的 Access Token 能被解析出用户 ID")
    void accessTokenCarriesUserId() {
        String token = jwtUtil.generateAccessToken(42L, "student01", 2);
        assertThat(jwtUtil.getUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("签发的 Access Token 能被解析出用户名")
    void accessTokenCarriesUsername() {
        String token = jwtUtil.generateAccessToken(42L, "student01", 2);
        assertThat(jwtUtil.getUsername(token)).isEqualTo("student01");
    }

    @Test
    @DisplayName("签发的 Access Token 能被解析出角色")
    void accessTokenCarriesRole() {
        assertThat(jwtUtil.getRole(jwtUtil.generateAccessToken(1L, "teacher1", 1))).isEqualTo(1);
        assertThat(jwtUtil.getRole(jwtUtil.generateAccessToken(2L, "admin", 0))).isEqualTo(0);
        assertThat(jwtUtil.getRole(jwtUtil.generateAccessToken(3L, "student01", 2))).isEqualTo(2);
    }

    @Test
    @DisplayName("有效 Token 校验通过")
    void validatesFreshToken() {
        String token = jwtUtil.generateAccessToken(1L, "teacher1", 1);
        assertThat(jwtUtil.validate(token)).isTrue();
    }

    @Test
    @DisplayName("Refresh Token 与 Access Token 携带同一份身份")
    void refreshTokenCarriesSameIdentity() {
        String refresh = jwtUtil.generateRefreshToken(7L, "student02", 2);
        assertThat(jwtUtil.getUserId(refresh)).isEqualTo(7L);
        assertThat(jwtUtil.getUsername(refresh)).isEqualTo("student02");
        assertThat(jwtUtil.getRole(refresh)).isEqualTo(2);
    }

    @Test
    @DisplayName("已过期的 Token 校验失败")
    void rejectsExpiredToken() {
        // 负数过期时间 → 签发即过期
        JwtUtil expired = new JwtUtil(SECRET, -1000L, -1000L);
        String token = expired.generateAccessToken(1L, "teacher1", 1);
        assertThat(expired.validate(token)).isFalse();
        assertThat(expired.getUserId(token)).isNull();
    }

    @Test
    @DisplayName("被篡改的 Token 校验失败")
    void rejectsTamperedToken() {
        String token = jwtUtil.generateAccessToken(1L, "teacher1", 1);
        String tampered = token.substring(0, token.length() - 3) + "xyz";
        assertThat(jwtUtil.validate(tampered)).isFalse();
    }

    @Test
    @DisplayName("用别的密钥签发的 Token 不被接受")
    void rejectsTokenSignedByAnotherKey() {
        JwtUtil other = new JwtUtil("another-secret-key-must-also-be-at-least-256-bits-long-1234567890",
                1_800_000L, 604_800_000L);
        String foreign = other.generateAccessToken(1L, "teacher1", 1);
        assertThat(jwtUtil.validate(foreign)).isFalse();
    }

    @Test
    @DisplayName("乱码字符串不抛异常，只返回校验失败")
    void rejectsGarbageWithoutThrowing() {
        assertThat(jwtUtil.validate("not-a-jwt")).isFalse();
        assertThat(jwtUtil.getUserId("not-a-jwt")).isNull();
        assertThat(jwtUtil.getUsername("not-a-jwt")).isNull();
    }

    @Test
    @DisplayName("空串与 null 不抛异常")
    void rejectsBlankWithoutThrowing() {
        assertThat(jwtUtil.validate("")).isFalse();
        assertThat(jwtUtil.validate(null)).isFalse();
    }

    // ---------- Token 类型声明 ----------
    //
    // 这一组覆盖一个真实的越权缺陷：两种 Token 原本使用完全相同的 claims，
    // 只有过期时间不同，导致校验方无法分辨。后果是拿一个 30 分钟的 Access Token
    // 去调 refresh 接口会被接受，换出全新的 7 天 Refresh Token——
    // 「Access Token 短命以限制泄露窗口」这个设计前提就被绕过了。

    @Test
    @DisplayName("Access Token 的类型声明是 access")
    void accessTokenHasAccessType() {
        String token = jwtUtil.generateAccessToken(1L, "teacher1", 1);
        assertThat(jwtUtil.getTokenType(token)).isEqualTo(JwtUtil.TYPE_ACCESS);
        assertThat(jwtUtil.isAccessToken(token)).isTrue();
        assertThat(jwtUtil.isRefreshToken(token)).isFalse();
    }

    @Test
    @DisplayName("Refresh Token 的类型声明是 refresh")
    void refreshTokenHasRefreshType() {
        String token = jwtUtil.generateRefreshToken(1L, "teacher1", 1);
        assertThat(jwtUtil.getTokenType(token)).isEqualTo(JwtUtil.TYPE_REFRESH);
        assertThat(jwtUtil.isRefreshToken(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("两种 Token 除了有效期，类型也明确不同")
    void accessAndRefreshAreDistinguishable() {
        String access = jwtUtil.generateAccessToken(9L, "student01", 2);
        String refresh = jwtUtil.generateRefreshToken(9L, "student01", 2);

        // 身份信息相同
        assertThat(jwtUtil.getUserId(access)).isEqualTo(jwtUtil.getUserId(refresh));
        // 但类型必须能区分开
        assertThat(jwtUtil.getTokenType(access)).isNotEqualTo(jwtUtil.getTokenType(refresh));
    }

    @Test
    @DisplayName("无法解析的 Token 取类型返回 null，且两个判断都为假")
    void invalidTokenHasNoType() {
        assertThat(jwtUtil.getTokenType("not-a-jwt")).isNull();
        assertThat(jwtUtil.isRefreshToken("not-a-jwt")).isFalse();
        assertThat(jwtUtil.isAccessToken("not-a-jwt")).isFalse();
    }
}
