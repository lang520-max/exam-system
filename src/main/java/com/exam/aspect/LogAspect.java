package com.exam.aspect;

import com.exam.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * AOP 操作日志切面。
 *
 * <p>只记录「谁调了哪个方法、耗时多久、成功还是业务拒绝」，<b>刻意不记录参数值</b>。</p>
 *
 * <p><b>为什么不记参数值</b>：登录/注册接口的参数对象里带着明文密码。
 * 早期版本用 {@code Arrays.toString(args)} 把整个参数打出来，
 * 等于把用户密码写进日志文件——日志往往比数据库更容易被翻到
 * （运维、排障、日志采集平台都会读），这是一个真实的泄露面。
 * 需要排查具体入参时，应该在 Controller 里针对白名单字段单独打印。</p>
 *
 * <p><b>为什么业务异常用 WARN</b>：密码错误、重复交卷、越权访问都是
 * 「预期内的业务结果」，不是系统故障。全部记成 ERROR 会让真正的故障
 * 淹没在噪声里——排障时最怕的就是这个。</p>
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    /** 超过这个耗时认为是慢请求，提一级到 WARN 便于发现 */
    private static final long SLOW_MILLIS = 1000L;

    @Pointcut("execution(* com.exam.controller.*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();

        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= SLOW_MILLIS) {
                log.warn("[操作日志] {}.{} 慢请求, 耗时 {}ms", className, methodName, elapsed);
            } else {
                log.info("[操作日志] {}.{} 成功, 耗时 {}ms", className, methodName, elapsed);
            }
            return result;
        } catch (BusinessException e) {
            // 业务异常 = 调用方预期内的拒绝（参数不合法、权限不足、状态冲突…）
            log.warn("[操作日志] {}.{} 业务拒绝[{}]: {}",
                    className, methodName, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[操作日志] {}.{} 系统异常, 耗时 {}ms, {}: {}",
                    className, methodName, elapsed, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }
}