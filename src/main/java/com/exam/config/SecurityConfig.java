package com.exam.config;

import com.exam.auth.JwtAuthenticationFilter;
import com.exam.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Spring Security 配置 —— JWT 无状态鉴权
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // 接入 CorsConfig 提供的 CorsConfigurationSource（默认不放行任何跨域来源）。
            // 走 Security 的 cors() 而不是独立 CorsFilter，预检与鉴权的顺序才确定。
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // 公开接口
                auth.requestMatchers("/api/auth/login", "/api/auth/register",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                    // 健康检查：容器 HEALTHCHECK 与 nginx /health 都打这里。
                    // 它不能要求鉴权——否则探针永远拿到 401，容器永远 unhealthy。
                    // 只暴露 health 端点（见 application.yml 的 management.endpoints），
                    // 且 show-details=never，不对外泄露数据库等内部状态。
                    .requestMatchers("/actuator/health").permitAll()
                    // 人脸功能的公开配置（只返回开关/阈值，不含任何用户数据）：
                    // 登录页要在注册时决定「要不要采集人脸」，考试列表要在点开始考试前
                    // 决定「要不要先核验」，而这两处此刻可能还没有 token。
                    .requestMatchers("/api/face/config").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/error").permitAll();

                // H2 控制台**只在 dev 档放行**。
                // 生产档 spring.h2.console.enabled 默认 false、控制台本来就不可达，
                // 但把这条 permitAll 永久写死在代码里，日后有人误开就是一个
                // 「无需鉴权、直连数据库」的后门。用 profile 把它锁住。
                if (environment.matchesProfiles("dev")) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }

                // 前端页面与静态资源全部放行：
                // 这些 HTML 只是「空壳」，本身不含任何数据；
                // 真正的数据一律走受保护的 /api/**，由 JWT + @PreAuthorize 把关。
                // 因此把页面本身设为公开是安全的，也避免了「未登录时
                // 连登录页都被 401 挡住」这种自锁死的情况。
                auth.requestMatchers("/", "/index.html", "/login.html").permitAll()
                    .requestMatchers("/*.html", "/student/**", "/teacher/**", "/admin/**").permitAll()
                    .requestMatchers("/css/**", "/js/**", "/favicon.ico", "/assets/**").permitAll()
                    // 第三方前端资源（本地化的 KaTeX）。
                    // 必须放行：浏览器加载 <link>/<script> 时不会带 Authorization 头，
                    // 若落到 anyRequest().authenticated() 就会 401，公式字体与脚本全部加载失败。
                    .requestMatchers("/vendor/**").permitAll()
                    // 管理员接口
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    // 其他接口一律需要登录（教师/学生由方法级 @PreAuthorize 再细分）
                    .anyRequest().authenticated();
            })
            // 未登录返回 401 JSON、越权返回 403 JSON，
            // 而不是让 Spring Security 默认吐一个没有响应体的裸 403
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "请先登录后再操作"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJson(response, HttpServletResponse.SC_FORBIDDEN, 403, "该操作仅教师（管理员）可执行")))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 统一的 JSON 错误响应，与业务接口的 Result 结构保持一致 */
    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}