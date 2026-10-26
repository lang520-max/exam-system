package com.exam.aspect;

import com.exam.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 幂等防重复提交切面
 *
 * <p>基于 Redis SETNX + EXPIRE 原子操作实现幂等。
 * 每次请求需要携带 {@code X-Request-Id} 请求头，
 * 相同 requestId 在 30 秒内重复提交会被拒绝。</p>
 *
 * <p>面试重点：
 * <ul>
 *   <li>Redis Lua 脚本的原子性</li>
 *   <li>SETNX 与 EXPIRE 的原子操作</li>
 *   <li>请求级别的幂等（不同于用户级别的防重复）</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotentAspect {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:request:";
    private static final long IDEMPOTENT_TTL = 30; // 30秒

    private final StringRedisTemplate redisTemplate;

    public IdempotentAspect(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 当前登录用户 id；取不到（如未认证）时用 anonymous，保证 key 仍然可用。
     */
    private static String currentUserId() {
        try {
            var authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication != null
                    && authentication.getPrincipal() instanceof com.exam.auth.JwtUser jwtUser) {
                return String.valueOf(jwtUser.getId());
            }
        } catch (Exception e) {
            // 忽略：拿不到身份不影响幂等本身
        }
        return "anonymous";
    }

    @Pointcut("@annotation(com.exam.aspect.Idempotent)")
    public void idempotentPointcut() {}

    @Around("idempotentPointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // Redis 不可用时跳过幂等检查
        if (redisTemplate == null) {
            return pjp.proceed();
        }

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String requestId = request.getHeader("X-Request-Id");

        if (requestId == null || requestId.isEmpty()) {
            throw new BusinessException("缺少幂等请求头: X-Request-Id");
        }

        // key 里带上用户：requestId 由客户端提供，若全局共用同一个命名空间，
        // 一个客户端只要猜到（或复用）别人的 requestId，就能把对方的正常提交
        // 判成「重复提交」挡下来。按用户隔离后这类干扰不存在。
        String key = IDEMPOTENT_KEY_PREFIX + currentUserId() + ":" + requestId;

        // Lua 脚本：SETNX + EXPIRE 原子操作
        String luaScript = "" +
                "local key = KEYS[1]\n" +
                "local ttl = ARGV[1]\n" +
                "local result = redis.call('SETNX', key, '1')\n" +
                "if result == 1 then\n" +
                "    redis.call('EXPIRE', key, ttl)\n" +
                "    return 1\n" +
                "end\n" +
                "return 0\n";

        Long result = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                Collections.singletonList(key),
                String.valueOf(IDEMPOTENT_TTL));

        if (result == null || result == 0) {
            log.warn("幂等拦截: requestId={}", requestId);
            throw new BusinessException("请勿重复提交，requestId=" + requestId);
        }

        try {
            return pjp.proceed();
        } catch (Exception e) {
            // 发生异常时删除幂等键，允许重试
            redisTemplate.delete(key);
            throw e;
        }
    }
}