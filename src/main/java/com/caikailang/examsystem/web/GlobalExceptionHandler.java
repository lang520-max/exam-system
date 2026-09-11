package com.caikailang.examsystem.web;

import com.caikailang.examsystem.common.BusinessException;
import com.caikailang.examsystem.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全局异常处理：把异常统一转换成 {@link Result}，避免堆栈直接暴露给前端。
 *
 * <p>只作用于 {@code web.api} 包下的 REST 控制器；页面控制器抛出的异常仍走
 * Spring Boot 默认的错误页，不会把 JSON 塞进 HTML 响应。</p>
 *
 * @author 蔡开浪
 */
@RestControllerAdvice(basePackages = "com.caikailang.examsystem.web.api")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，返回业务码 + 友好提示（不记 error，避免刷日志） */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败：取<b>第一条</b>校验提示返回。
     *
     * <p>一次返回所有字段的错误对前端并不友好（弹窗要显示一长串），
     * 只回第一条、让用户逐个修正，是表单类接口更常见的做法。</p>
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidation(BindException e) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors();
        String message = errors.isEmpty() ? "参数校验失败" : errors.get(0).getDefaultMessage();
        log.warn("参数校验失败: {}", message);
        return Result.fail(message);
    }

    /** 兜底：未知异常记 error 日志（含堆栈）便于排查，前端只看到统一提示 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
