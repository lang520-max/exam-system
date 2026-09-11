package com.caikailang.examsystem.config;

import com.caikailang.examsystem.security.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册登录拦截器、提供密码编码器。
 *
 * @author lang520-max
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
            .addPathPatterns("/**")
            .excludePathPatterns("/css/**", "/js/**", "/images/**", "/error", "/favicon.ico");
    }

    /**
     * BCrypt：自带随机盐，同一密码每次哈希结果都不同，可抗彩虹表；校验走
     * {@link PasswordEncoder#matches(CharSequence, String)}，不涉及明文比较。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
