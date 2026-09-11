package com.caikailang.examsystem.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要登录才能访问的接口或页面，由 {@link AuthInterceptor} 统一拦截校验。
 *
 * <p>服务端只信任 Session 中的登录用户，任何前端传来的 userId 都不作为身份依据。</p>
 *
 * @author lang520-max
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
