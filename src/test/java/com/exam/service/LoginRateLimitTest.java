package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.common.utils.JwtUtil;
import com.exam.dto.request.LoginForm;
import com.exam.entity.User;
import com.exam.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录限流测试 —— 用 Mockito 模拟 Redis，不依赖真实中间件。
 *
 * <p>覆盖的缺陷：修复前 {@code login()} 没有任何失败计数，
 * 连打 10 次错误密码全部返回 401（实测），既无限流也无锁定。
 * 配合生产库里口令为 {@code 123456} 的演示账号，爆破成本几乎为零。</p>
 *
 * <p>限流由 Redis 实现，而 dev/test 档没有 Redis，
 * 因此必须把 {@code StringRedisTemplate} 打桩才能覆盖到真正的分支。</p>
 */
@DisplayName("登录限流")
class LoginRateLimitTest {

    private static final String USERNAME = "student01";
    /** 与 AuthService.MAX_LOGIN_ATTEMPTS 保持一致 */
    private static final int MAX_ATTEMPTS = 5;
    /** 登录成功时辅助方法返回的哨兵值 */
    private static final int OK = 200;

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    /** 模拟 Redis 里的失败计数器 */
    private AtomicInteger counter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        counter = new AtomicInteger();

        User user = new User();
        user.setId(1L);
        user.setUsername(USERNAME);
        user.setPassword("$2a$10$stub");
        user.setRole(2);
        user.setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        // 默认所有密码都错；单个用例再按需改成匹配成功
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> {
            int c = counter.get();
            return c == 0 ? null : String.valueOf(c);
        });
        when(valueOps.increment(anyString())).thenAnswer(inv -> (long) counter.incrementAndGet());
    }

    /** 传入 null 的 redis 即模拟「dev/测试档没有 Redis」 */
    private AuthService service(StringRedisTemplate redis) {
        return new AuthService(userMapper, passwordEncoder, mock(JwtUtil.class), redis);
    }

    private LoginForm form(String password) {
        LoginForm f = new LoginForm();
        f.setUsername(USERNAME);
        f.setPassword(password);
        return f;
    }

    /** 执行一次登录，返回业务码；成功则返回 OK */
    private int loginCode(AuthService auth, String password) {
        try {
            auth.login(form(password));
            return OK;
        } catch (BusinessException e) {
            return e.getCode();
        }
    }

    /** 连续失败 n 次 */
    private void failTimes(AuthService auth, int n) {
        for (int i = 0; i < n; i++) {
            loginCode(auth, "wrong-" + i);
        }
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("前 5 次错误密码返回 401（不误伤正常输错的同学）")
    void firstAttempts_return401() {
        AuthService auth = service(redisTemplate);

        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            codes.add(loginCode(auth, "wrong-" + i));
        }

        assertThat(codes).hasSize(MAX_ATTEMPTS).containsOnly(401);
    }

    @Test
    @DisplayName("【关键】第 6 次错误密码被限流，返回 429")
    void attemptBeyondLimit_returns429() {
        AuthService auth = service(redisTemplate);

        failTimes(auth, MAX_ATTEMPTS);

        assertThat(loginCode(auth, "wrong-again")).isEqualTo(429);
    }

    @Test
    @DisplayName("被锁定后即使密码正确也拒绝")
    void lockedAccount_rejectedEvenWithCorrectPassword() {
        AuthService auth = service(redisTemplate);
        failTimes(auth, MAX_ATTEMPTS);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThat(loginCode(auth, "correct")).isEqualTo(429);
    }

    @Test
    @DisplayName("登录成功会清零失败计数")
    void success_clearsFailureCounter() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        AuthService auth = service(redisTemplate);

        assertThat(loginCode(auth, "correct")).isEqualTo(OK);

        verify(redisTemplate).delete("login:fail:" + USERNAME);
    }

    @Test
    @DisplayName("用户名不存在也计入失败次数（否则可用「是否被限流」枚举账号）")
    void unknownUser_alsoCountsAsFailure() {
        when(userMapper.selectOne(any())).thenReturn(null);
        AuthService auth = service(redisTemplate);

        assertThat(loginCode(auth, "whatever")).isEqualTo(401);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有 Redis 时限流整体关闭，dev/测试档不受影响")
    void withoutRedis_noLimit() {
        AuthService auth = service(null);

        for (int i = 0; i < MAX_ATTEMPTS * 3; i++) {
            // 始终 401，永远不会变成 429
            assertThat(loginCode(auth, "wrong-" + i)).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("Redis 抛异常时放行，而不是把所有人挡在门外")
    void redisFailure_failsOpen() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        AuthService auth = service(broken);

        // 不应因为 Redis 抖动就返回 429 或抛 500
        assertThat(loginCode(auth, "wrong")).isEqualTo(401);
    }
}