package com.exam.common.utils;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类封装（仅在 Redis 启用时加载）
 *
 * <p>dev / test 档会排除 Redis 自动装配，此时本 Bean 不创建，
 * 依赖它的组件一律走「降级为数据库」的分支。</p>
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    public RedisUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 设置值 */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 设置值（带过期时间） */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 获取值 */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 删除 */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /** 设置过期时间 */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /** 获取剩余过期时间 */
    public long getExpire(String key, TimeUnit unit) {
        Long expire = redisTemplate.getExpire(key, unit);
        return expire != null ? expire : -1;
    }

    /** 判断是否存在 */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** SETNX（不存在时设置） */
    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    /** 原子自增 */
    public long increment(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        return count != null ? count : 0;
    }
}