package com.exam.service;

import com.exam.auth.JwtUser;
import com.exam.common.exception.BusinessException;
import com.exam.common.utils.JwtUtil;
import com.exam.dto.request.LoginForm;
import com.exam.dto.request.PasswordForm;
import com.exam.dto.request.RegisterForm;
import com.exam.dto.response.LoginVO;
import com.exam.entity.User;
import com.exam.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;

/**
 * 认证服务：注册、登录、Token 刷新
 */
@Slf4j
@Service
public class AuthService {

    /** 登录失败计数 key */
    private static final String LOGIN_FAIL_KEY = "login:fail:%s";
    /** 允许的连续失败次数，达到即锁定 */
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    /** 计数窗口 / 锁定时长 */
    private static final Duration LOGIN_LOCK_WINDOW = Duration.ofMinutes(10);

    /**
     * 已登出的 Refresh Token 黑名单 key。
     *
     * <p>存的是 token 的 <b>SHA-256 摘要</b>而不是原文：Redis 里不该出现
     * 可直接使用的凭证 —— 备份文件、慢日志、运维随手 {@code KEYS *} 都会泄露它。</p>
     */
    private static final String REVOKED_REFRESH_KEY = "auth:revoked-refresh:%s";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    /** Redis 不可用（dev / 测试档）时为 null，此时登录限流整体关闭 */
    private final StringRedisTemplate redisTemplate;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 注册学生账号
     */
    @Transactional
    public User register(RegisterForm form) {
        // 检查用户名是否已存在
        User exists = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, form.getUsername()));
        if (exists != null) {
            throw new BusinessException("该用户名已被注册");
        }

        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRealName(form.getRealName());
        user.setEmail(form.getEmail());
        user.setRole(2); // 强制学生角色
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该用户名已被注册");
        }

        return hidePassword(userMapper.selectById(user.getId()));
    }

    /**
     * 抹掉密码哈希再返回给控制器。
     *
     * <p>响应体会经过浏览器、代理、日志与前端缓存，BCrypt 哈希属于敏感信息，
     * 不应该出网。{@code /api/auth/me} 一直是这么做的，注册路径此前漏了。</p>
     */
    private static User hidePassword(User user) {
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    /**
     * 登录
     */
    public LoginVO login(LoginForm form) {
        // 先查是否已被锁定，避免继续消耗 BCrypt 计算
        assertNotLocked(form.getUsername());

        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, form.getUsername()));
        if (user == null) {
            // 用户名不存在也计入失败次数：否则攻击者可以用「是否被限流」
            // 反推账号是否存在（用户名枚举）。
            recordLoginFailure(form.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException(401, "账号已被禁用");
        }
        if (!passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            recordLoginFailure(form.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 登录成功清零，避免历史失败次数累积到下一次
        clearLoginFailures(form.getUsername());

        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole()));
        vo.setRefreshToken(jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole()));
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setRealName(user.getRealName());
        return vo;
    }

    /**
     * 刷新 Token
     *
     * <p><b>必须校验 token 类型</b>：Access Token 与 Refresh Token 同为合法签名，
     * 若此处不区分类型，任何一个短命的 Access Token 都能换出一整套全新的 Token
     * （含 7 天的 Refresh Token）——「Access Token 短命以限制泄露窗口」这个前提就失效了。
     */
    public LoginVO refresh(String refreshToken) {
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(401, "Refresh Token 无效或已过期");
        }
        // 已登出的 token 不能再换新 token（登出接口做的就是这个）
        if (isRefreshTokenRevoked(refreshToken)) {
            throw new BusinessException(401, "该 Refresh Token 已退出登录，请重新登录");
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String username = jwtUtil.getUsername(refreshToken);
        Integer role = jwtUtil.getRole(refreshToken);

        if (userId == null || username == null) {
            throw new BusinessException(401, "Token 无效或已过期");
        }

        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(userId, username, role));
        vo.setRefreshToken(jwtUtil.generateRefreshToken(userId, username, role));
        vo.setUserId(userId);
        vo.setUsername(username);
        vo.setRole(role);
        return vo;
    }

    /**
     * 自助修改密码。
     *
     * <p><b>为什么要有它</b>：登录页一直写着「忘记密码请联系教务」——密码一旦泄露，
     * 学生与教师都没法自己换掉，只能等人工处理（用户直接反馈的问题）。</p>
     *
     * <p><b>改完顺手拉黑当前 refresh token</b>，调用方随后必须重新登录。
     * 无状态 JWT 下已经签发的 access token 仍能用到过期（默认 30 分钟），这是固有代价；
     * 但至少不能让旧 refresh token 再换出新的 access token —— 否则「改了密码却等于没改」。</p>
     *
     * <p>旧密码错、或新密码与旧密码相同都直接拒绝：前者防「别人拿到你的手机就能改」，
     * 后者防「改了个寂寞」。</p>
     */
    @Transactional
    public void changePassword(Long userId, PasswordForm form, String refreshToken) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在或已注销");
        }
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "原密码不正确");
        }
        if (passwordEncoder.matches(form.getNewPassword(), user.getPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }

        // 只更新 password 一个字段：updateById 默认忽略 null 字段，
        // 因此不会把 username/role/status 覆盖成空（与题库/题目更新同一套白名单思路）
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userMapper.updateById(update);

        logout(refreshToken);
        log.info("用户 {} 自助修改了密码，当前 refresh token 已拉黑，需重新登录", user.getUsername());
    }

    /**
     * 登出：把 Refresh Token 加入黑名单。
     *
     * <p><b>能做到什么、不能做到什么</b>：JWT 是无状态的，服务端没有「会话」可销毁。
     * 这里拉黑 refresh token，使它无法再换出新的 access token；
     * 前端同时清掉本地两个 token。已经签发的 <b>access token 在剩余有效期内仍然可用</b> ——
     * 这是无状态 JWT 的固有代价：要做到即时失效，必须引入 token 版本号或每次请求查库，
     * 本项目按成本取舍不做这一步（见交接文档的已知限制）。</p>
     *
     * <p>没有 Redis 时（dev / test 档）只记一条 warn 并返回成功：登出对前端依然有效
     * （本地清理），只是无法阻止旧 refresh token 被复用。</p>
     *
     * <p>幂等：没带 token、或重复调用都算成功。</p>
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        if (redisTemplate == null) {
            log.warn("未启用 Redis，Refresh Token 无法加入黑名单：登出仅清理了客户端状态");
            return;
        }
        try {
            redisTemplate.opsForValue().set(revokedKey(refreshToken), "1", remainingTtl(refreshToken));
        } catch (Exception e) {
            log.error("写入 Refresh Token 黑名单失败: {}", e.getMessage());
        }
    }

    /** 该 Refresh Token 是否已被登出拉黑 */
    public boolean isRefreshTokenRevoked(String refreshToken) {
        if (redisTemplate == null || refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey(refreshToken)));
        } catch (Exception e) {
            // Redis 抖动时不能把所有人挡在刷新之外。与登录限流同样选择「放行」，
            // 代价是抖动期间被拉黑的 token 可能被复用一次 —— 刻意接受的降级。
            log.warn("读取 Refresh Token 黑名单失败，本次放行: {}", e.getMessage());
            return false;
        }
    }

    private static String revokedKey(String refreshToken) {
        return String.format(REVOKED_REFRESH_KEY, sha256(refreshToken));
    }

    /** 黑名单条目的存活时间 = 该 token 的剩余有效期（过期后条目自然消失，不占空间） */
    private Duration remainingTtl(String refreshToken) {
        Date expiration = null;
        var claims = jwtUtil.parseToken(refreshToken);
        if (claims != null) {
            expiration = claims.getExpiration();
        }
        long remain = expiration == null ? 0 : expiration.getTime() - System.currentTimeMillis();
        // 已经过期的 token 本来也换不出东西，给个短 TTL 兜底即可
        return remain > 0 ? Duration.ofMillis(remain) : Duration.ofMinutes(1);
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                  .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须支持的算法，正常走不到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    // ==================== 登录限流 ====================
    //
    // 设计取舍：计数按【用户名】而不是 IP。
    //   - 按 IP：同一 NAT 出口下的整间机房会互相连坐，校园网场景尤其糟；
    //   - 按用户名：攻击者可以用 5 次错误密码把某个同学「锁」10 分钟（定向 DoS）。
    // 考试系统里后者危害更小且可被教师手工解锁，因此选按用户名。
    //
    // 可用性取舍：Redis 异常时**放行**而不是拒绝。
    // 登录是所有人进入系统的唯一入口，不能因为 Redis 抖动就把全体用户挡在门外。
    // 代价是 Redis 挂掉期间限流失效——这是刻意接受的降级。

    private static String failKey(String username) {
        return String.format(LOGIN_FAIL_KEY, username);
    }

    private void assertNotLocked(String username) {
        if (redisTemplate == null) {
            return; // dev / 测试档没有 Redis，限流关闭
        }
        try {
            int n = parseCount(redisTemplate.opsForValue().get(failKey(username)));
            if (n >= MAX_LOGIN_ATTEMPTS) {
                throw new BusinessException(429,
                        "登录失败次数过多，请 " + LOGIN_LOCK_WINDOW.toMinutes() + " 分钟后再试");
            }
        } catch (BusinessException e) {
            throw e; // 业务异常原样抛出，别被下面的 catch 吞掉
        } catch (Exception e) {
            log.warn("登录限流检查失败，本次放行: {}", e.getMessage());
        }
    }

    private void recordLoginFailure(String username) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String key = failKey(username);
            Long n = redisTemplate.opsForValue().increment(key);
            // 第一次失败时才设过期，窗口从首次失败开始计
            if (n != null && n == 1L) {
                redisTemplate.expire(key, LOGIN_LOCK_WINDOW);
            }
        } catch (Exception e) {
            log.warn("登录失败计数写入异常，本次跳过: {}", e.getMessage());
        }
    }

    private void clearLoginFailures(String username) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(failKey(username));
        } catch (Exception e) {
            log.warn("登录失败计数清理异常: {}", e.getMessage());
        }
    }

    private static int parseCount(String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取当前用户信息
     */
    public User getCurrentUser(JwtUser jwtUser) {
        if (jwtUser == null) {
            throw new BusinessException(401, "未登录");
        }
        User user = userMapper.selectById(jwtUser.getId());
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        user.setPassword(null); // 不返回密码
        return user;
    }
}