package com.exam.common;

import com.exam.common.exception.BusinessException;
import com.exam.common.utils.JwtUtil;
import com.exam.dto.response.LoginVO;
import com.exam.mapper.UserMapper;
import com.exam.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登出与 Refresh Token 黑名单。
 *
 * <p>dev / test 档没有 Redis（黑名单整体降级），所以这里用 mock 的
 * {@link StringRedisTemplate} 直接验证：<b>写进去的是 token 的摘要而不是原文</b>、
 * 被拉黑的 token 换不出新 token、以及 Redis 故障时不把所有人挡在刷新之外。</p>
 */
@DisplayName("登出 · Refresh Token 黑名单")
class AuthLogoutTest {

    private static final String SECRET =
            "logout-test-secret-key-must-be-at-least-256-bits-long-abcdef";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 1_800_000L, 604_800_000L);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private AuthService serviceWithRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new AuthService(userMapper, passwordEncoder, jwtUtil, redisTemplate);
    }

    private String refreshToken() {
        return jwtUtil.generateRefreshToken(7L, "student01", 2);
    }

    @Test
    @DisplayName("登出把 token 的 SHA-256 摘要写进黑名单，而不是 token 原文")
    void logout_storesHashNotRawToken() {
        AuthService service = serviceWithRedis();
        String refresh = refreshToken();

        service.logout(refresh);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(key.capture(), eq("1"), ttl.capture());

        assertThat(key.getValue()).startsWith("auth:revoked-refresh:");
        assertThat(key.getValue())
                .as("Redis 里不该出现可直接使用的凭证（备份/慢日志/KEYS * 都会泄露）")
                .doesNotContain(refresh);
        assertThat(ttl.getValue()).as("TTL 应取该 token 的剩余有效期").isPositive();
    }

    @Test
    @DisplayName("【关键】被登出拉黑的 Refresh Token 不能再换新 Token")
    void revokedRefreshToken_cannotBeRefreshed() {
        AuthService service = serviceWithRedis();
        String refresh = refreshToken();
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.refresh(refresh))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已退出登录");
    }

    @Test
    @DisplayName("没被拉黑时刷新照常成功（别把功能一起修坏）")
    void activeRefreshToken_stillWorks() {
        AuthService service = serviceWithRedis();
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        LoginVO vo = service.refresh(refreshToken());

        assertThat(vo.getAccessToken()).isNotBlank();
        assertThat(vo.getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("没有 Redis（dev/test 档）时登出仍然成功，只是无法吊销")
    void logout_withoutRedis_isGraceful() {
        AuthService service = new AuthService(userMapper, passwordEncoder, jwtUtil, null);
        String refresh = refreshToken();

        assertThatCode(() -> service.logout(refresh)).doesNotThrowAnyException();
        assertThat(service.isRefreshTokenRevoked(refresh)).isFalse();

        // 降级语义写清楚：登出只清理了客户端状态，旧 refresh token 仍能换新 token
        assertThat(service.refresh(refresh).getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("Redis 故障时刷新放行（可用性优先，与登录限流同一取舍）")
    void redisFailure_doesNotBlockRefresh() {
        AuthService service = serviceWithRedis();
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isRefreshTokenRevoked(refreshToken())).isFalse();
        assertThat(service.refresh(refreshToken()).getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("登出幂等：不带 token 也不报错")
    void logout_withoutToken_isNoOp() {
        AuthService service = serviceWithRedis();

        assertThatCode(() -> service.logout(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.logout("")).doesNotThrowAnyException();
    }
}