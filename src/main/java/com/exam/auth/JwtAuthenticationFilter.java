package com.exam.auth;

import com.exam.common.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器 —— 从请求头中提取 Token 并校验
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        // ★ 只接受 access token。
        //
        // refresh token 有效期 7 天，此前只验签不验类型，于是它可以直接放进
        // Authorization 头调用任意 /api/** —— 「access 短命 30 分钟以限制泄露窗口」
        // 这个设计前提就被绕过了一半。上一轮只堵了反方向（access 不能换 refresh），
        // 这个方向一直敞着。
        if (StringUtils.hasText(token) && jwtUtil.isAccessToken(token)) {
            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            Integer role = jwtUtil.getRole(token);

            if (userId != null && username != null) {
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(getRoleName(role));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                new JwtUser(userId, username, role),
                                null,
                                Collections.singletonList(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } else if (StringUtils.hasText(token)) {
            // 不带认证信息继续走过滤器链：受保护接口会由 Security 正常返回 401，
            // 而不是在这里抛异常（也不把「token 类型不对」与「token 无效」区分给调用方）
            log.debug("忽略非 access 类型或无效的 token");
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private String getRoleName(Integer role) {
        if (role == null) return "ROLE_STUDENT";
        switch (role) {
            case 0: return "ROLE_ADMIN";
            case 1: return "ROLE_TEACHER";
            default: return "ROLE_STUDENT";
        }
    }
}