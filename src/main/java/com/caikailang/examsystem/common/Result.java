package com.caikailang.examsystem.common;

import java.io.Serializable;

/**
 * 统一响应结构：所有 REST 接口都返回该结构，前端只需判断 {@code code == 0}。
 *
 * <p>约定：0 成功、400 业务/参数错误、401 未登录、403 越权、404 资源不存在、500 系统异常。</p>
 *
 * @param <T> 业务数据类型
 * @author lang520-max
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 表示成功，非 0 表示失败 */
    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<T>(0, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<T>(0, "success", null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<T>(400, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
