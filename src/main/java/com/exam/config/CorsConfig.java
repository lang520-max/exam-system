package com.exam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p><b>默认不对外开放任何跨域来源。</b></p>
 *
 * <p>原实现是 {@code addAllowedOriginPattern("*")} 配合 {@code allowCredentials(true)}，
 * 这会让服务端把任意请求方 Origin 原样回显，并带上
 * {@code Access-Control-Allow-Credentials: true}——任何网站都能发起带凭证的跨域请求
 * 并读取响应。</p>
 *
 * <p>当前危害有限，因为本项目的鉴权是「localStorage 里的 JWT + Authorization 头」，
 * 而不是 Cookie：异源页面读不到别人的 localStorage，所以拿不到 token。
 * 但这仍是不该发布的默认值——一旦将来改用 Cookie 鉴权，
 * 它会立刻变成一个可直接利用的凭证窃取/CSRF 漏洞。</p>
 *
 * <p>而且本项目的页面与接口<b>同源</b>（nginx 把静态资源与 /api 一起反代到 app），
 * 正常运行时根本不需要 CORS。所以这里默认留空：
 * 需要跨域时（例如前端独立部署、或用 Vite dev server 调试）再显式配置：</p>
 *
 * <pre>
 * app:
 *   cors:
 *     allowed-origins: http://localhost:5173,https://exam.example.com
 * </pre>
 */
@Configuration
public class CorsConfig {

    /** 允许的跨域来源，逗号分隔；默认空 = 不允许任何跨域 */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 交给 Spring Security 使用（{@code SecurityConfig} 里的 {@code .cors()}），
     * 而不是注册成独立的 {@code CorsFilter} Bean。
     *
     * <p>这样做的好处是 CORS 进入 Security 过滤器链内部，
     * 预检请求（OPTIONS）与鉴权、401/403 响应体的处理顺序是明确的；
     * 独立 CorsFilter 的注册顺序取决于 Spring Boot 的过滤器排序，
     * 容易出现「预检被鉴权拦截」这类难查的问题。</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = parseOrigins(allowedOrigins);

        // 只有显式配置了来源才启用跨域。空配置下 CorsConfiguration 不含任何
        // allowedOrigin，跨域请求会被拒绝，而同源请求不受影响
        // （同源请求不算 CORS 请求，不会走到这里）。
        if (!origins.isEmpty()) {
            config.setAllowedOriginPatterns(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}