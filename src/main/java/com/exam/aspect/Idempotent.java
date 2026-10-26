package com.exam.aspect;

import java.lang.annotation.*;

/**
 * 幂等注解 —— 标记需要防重复提交的接口
 *
 * <p>使用方式：
 * <pre>{@code
 * @Idempotent
 * @PostMapping("/submit")
 * public Result<?> submit(@RequestBody ExamSubmitDTO form) { ... }
 * }</pre>
 * 调用方需要在请求头中携带 {@code X-Request-Id} 唯一标识。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
}