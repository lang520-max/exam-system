package com.caikailang.examsystem.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记仅「教师 / 管理员」可访问的接口或页面，由 {@link AuthInterceptor} 校验角色。
 *
 * <p>本系统只有 {@code TEACHER}（教师，即管理员）与 {@code STUDENT}（学生）两种角色，
 * 题库维护、组卷、成绩统计等写操作都要求 {@code TEACHER}。</p>
 *
 * <p>拦截器只是第一道防线：Service 层仍会再校验一次角色，避免将来新增入口时漏标注解。</p>
 *
 * @author lang520-max
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
