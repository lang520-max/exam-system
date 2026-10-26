package com.exam.config;

import com.exam.common.utils.JwtUtil;
import com.exam.websocket.ExamWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * WebSocket 静态注入配置
 *
 * <p>Spring Boot 的依赖注入对 @ServerEndpoint 无效，
 * 需要通过此配置手动将 Spring Bean 注入到 WebSocket 处理器中。</p>
 */
@Configuration
@ConditionalOnProperty(name = "app.websocket.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketBeanConfig {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public WebSocketBeanConfig(JwtUtil jwtUtil, @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        ExamWebSocketHandler.setStaticBeans(jwtUtil, redisTemplate);
    }
}