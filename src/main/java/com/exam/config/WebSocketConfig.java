package com.exam.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置。
 *
 * <p>开关与 Redis 解耦：倒计时优先读 Redis TTL，没有 Redis 时降级用
 * 前端带过来的 deadline 计算剩余时间，所以「没有 Redis」不该导致
 * WebSocket 端点整个消失。测试档单独关掉它——因为
 * {@code ServerEndpointExporter} 需要一个真实的 Servlet 容器，
 * 而 {@code @SpringBootTest}（mock web 环境）并没有。</p>
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "app.websocket.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}