package com.caikailang.examsystem.common;

/**
 * 业务异常：用于表达「可预期的业务规则拦截」（如重复交卷、题目不足、越权操作）。
 *
 * <p>由 {@link com.caikailang.examsystem.web.GlobalExceptionHandler} 统一转换为
 * {@link Result} 响应，避免把业务错误当成 500 系统异常抛出，也避免堆栈直接暴露给前端。</p>
 *
 * @author lang520-max
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
