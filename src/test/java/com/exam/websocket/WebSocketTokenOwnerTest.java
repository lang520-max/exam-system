package com.exam.websocket;

import com.exam.common.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 连接鉴权测试。
 *
 * <p>覆盖的是一个真实的越权缺陷：{@code onOpen} 原本只校验「这是个合法 JWT」，
 * 从不检查 token 是否<b>属于</b> URL 路径里的 userId。
 * 而 onOpen 里有「多端登录互踢」逻辑——会关闭同一个 {@code examId:userId}
 * 的旧连接——于是任何已登录学生只要填上同学的 userId 就能把对方踢下线。</p>
 *
 * <p>这个包级可见的静态方法就是为此抽出来的：把判断从 {@code onOpen} 里独立出来，
 * 才能在没有 Servlet 容器的情况下直接测。</p>
 */
@DisplayName("WebSocket 鉴权 · token 必须属于路径里的 userId")
class WebSocketTokenOwnerTest {

    private static final String SECRET =
            "ws-owner-test-secret-key-must-be-at-least-256-bits-long-abcdef";
    private static final long ACCESS_TTL = 1_800_000L;
    private static final long REFRESH_TTL = 604_800_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void wireStaticBeans() {
        // 每个用例都重新注入，避免被别的测试类（Spring 上下文启动时会调 setStaticBeans）覆盖
        jwtUtil = new JwtUtil(SECRET, ACCESS_TTL, REFRESH_TTL);
        ExamWebSocketHandler.setStaticBeans(jwtUtil, null);
    }

    @Test
    @DisplayName("token 确实属于该 userId 时放行")
    void ownToken_allowed() {
        String token = jwtUtil.generateAccessToken(5L, "student01", 2);
        assertThat(ExamWebSocketHandler.tokenBelongsTo(token, 5L)).isTrue();
    }

    @Test
    @DisplayName("【关键】拿自己的合法 token 冒用别人的 userId 必须被拒绝")
    void foreignUserId_rejected() {
        // 攻击场景：攻击者(userId=5)用自己的 token 去连
        // /ws/exam/1/6 —— 6 是受害者。放行则受害者被踢下线。
        String attackerToken = jwtUtil.generateAccessToken(5L, "attacker", 2);

        assertThat(ExamWebSocketHandler.tokenBelongsTo(attackerToken, 6L)).isFalse();
    }

    @Test
    @DisplayName("签名无效的 token 被拒绝")
    void tamperedToken_rejected() {
        String token = jwtUtil.generateAccessToken(5L, "student01", 2);
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        assertThat(ExamWebSocketHandler.tokenBelongsTo(tampered, 5L)).isFalse();
    }

    @Test
    @DisplayName("【关键】Refresh Token 不能用来建 WebSocket 连接")
    void refreshToken_rejected() {
        // refresh token 有效期 7 天；若能拿它连 WebSocket，
        // 「access 短命」的设计前提就被绕过了（与过滤器同一个口径）
        String refresh = jwtUtil.generateRefreshToken(5L, "student01", 2);

        assertThat(ExamWebSocketHandler.tokenBelongsTo(refresh, 5L)).isFalse();
    }

    @Test
    @DisplayName("乱码 token 被拒绝且不抛异常")
    void garbageToken_rejected() {
        assertThat(ExamWebSocketHandler.tokenBelongsTo("not-a-jwt", 5L)).isFalse();
    }

    @Test
    @DisplayName("token 或 userId 为空时拒绝")
    void nullArguments_rejected() {
        String token = jwtUtil.generateAccessToken(5L, "student01", 2);

        assertThat(ExamWebSocketHandler.tokenBelongsTo(null, 5L)).isFalse();
        assertThat(ExamWebSocketHandler.tokenBelongsTo(token, null)).isFalse();
        assertThat(ExamWebSocketHandler.tokenBelongsTo(null, null)).isFalse();
    }

    @Test
    @DisplayName("已过期的 token 被拒绝")
    void expiredToken_rejected() {
        JwtUtil expired = new JwtUtil(SECRET, -1000L, -1000L);
        ExamWebSocketHandler.setStaticBeans(expired, null);

        String token = expired.generateAccessToken(5L, "student01", 2);

        assertThat(ExamWebSocketHandler.tokenBelongsTo(token, 5L)).isFalse();
    }
}