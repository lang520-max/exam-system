package com.exam.common.exception;

import com.exam.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>业务异常会把业务码映射成<b>真实的 HTTP 状态码</b>（401/403/404/409/400），
 * 而不是一律 200 + 响应体里塞个 code。这样网关、监控、前端 fetch 的
 * error 分支、以及压测工具的失败率统计都能正确识别失败——
 * 「HTTP 200 但业务失败」会让这些基础设施全部失灵。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    /** 业务码 → HTTP 状态码 */
    private static HttpStatus toHttpStatus(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.forbidden("权限不足");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数绑定失败");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 缺少必需请求头。
     *
     * <p>典型场景：交卷接口要求 {@code X-Request-Id}（幂等键）。
     * 这一类异常继承自 {@code ServletRequestBindingException}，
     * 若不为它单独写处理分支，就会掉进兜底的 Exception 处理器被报成 500，
     * 把「调用方漏传参数」误判成「服务端故障」。</p>
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingHeader(MissingRequestHeaderException e) {
        return Result.fail(400, "缺少必要请求头: " + e.getHeaderName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(400, "请求体格式错误");
    }

    /**
     * 唯一键冲突。
     *
     * <p>返回 <b>409</b> 而不是 200：并发下「同一资源被插了两次」是典型的冲突，
     * 用 200 表达会让网关与压测工具把它算成成功。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("Duplicate key: {}", e.getMessage());
        return Result.fail(409, "操作失败，数据已存在");
    }

    /**
     * 静态资源不存在。
     *
     * <p>最典型的来源是浏览器自动请求的 {@code /favicon.ico}。这类请求
     * <b>不是服务端故障</b>，但如果不单独处理，它会掉进下面的兜底
     * {@code Exception} 分支，被当作「系统繁忙」打出一整条堆栈——
     * 既污染日志、又会让人误以为程序有 bug。</p>
     *
     * <p>这里记 debug 并返回 404，把「客户端要了一个不存在的文件」
     * 如实表达出来。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());
        return Result.notFound("资源不存在: " + e.getResourcePath());
    }

    /**
     * 兜底：未预期的服务端异常。
     *
     * <p>必须显式声明 <b>500</b>。此前这个分支没有 {@code @ResponseStatus}，
     * Spring 于是返回 HTTP 200、只把 500 写在响应体里 ——
     * 结果是 NPE、SQL 错误这类真故障在 nginx / APM / 负载均衡的错误率统计里
     * 全部算成功，故障无法被监控发现。这与本类开头声明的原则自相矛盾。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("System error", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}