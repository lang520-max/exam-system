package com.caikailang.examsystem.security;

import com.caikailang.examsystem.common.BusinessException;
import com.caikailang.examsystem.common.Result;
import com.caikailang.examsystem.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录与角色拦截器。
 *
 * <p>只对被 {@link RequireLogin} / {@link RequireAdmin} 标记的处理器生效：
 * 未登录访问 REST 接口返回 401 JSON，访问页面则 302 重定向到登录页；
 * 角色不符返回 403。</p>
 *
 * @author lang520-max
 */
public class AuthInterceptor implements HandlerInterceptor {

    /** Session 中保存当前登录用户的键 */
    public static final String SESSION_USER = "LOGIN_USER";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        boolean needLogin = method.hasMethodAnnotation(RequireLogin.class)
            || method.getBeanType().isAnnotationPresent(RequireLogin.class);
        boolean needAdmin = method.hasMethodAnnotation(RequireAdmin.class)
            || method.getBeanType().isAnnotationPresent(RequireAdmin.class);
        if (!needLogin && !needAdmin) {
            return true;
        }

        User user = currentUser(request.getSession(false));
        if (user == null) {
            reject(request, response, 401, "请先登录后再操作");
            return false;
        }
        if (needAdmin && !user.isTeacher()) {
            reject(request, response, 403, "该操作仅教师（管理员）可执行");
            return false;
        }
        return true;
    }

    /** 从 Session 中取当前登录用户，未登录返回 null */
    public static User currentUser(HttpSession session) {
        return session == null ? null : (User) session.getAttribute(SESSION_USER);
    }

    /**
     * 取当前登录用户，未登录直接抛 401。
     *
     * <p>控制器统一通过它拿身份，绝不从请求参数里读 userId。</p>
     */
    public static User requireUser(HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        return user;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, int code, String message) throws Exception {
        if (isApiRequest(request)) {
            response.setStatus(code);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(objectMapper.writeValueAsBytes(Result.fail(code, message)));
            response.getOutputStream().flush();
        } else {
            response.sendRedirect(request.getContextPath() + "/login?redirect=" + request.getRequestURI());
        }
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return uri.startsWith(request.getContextPath() + "/api/")
            || (accept != null && accept.contains("application/json"));
    }
}
