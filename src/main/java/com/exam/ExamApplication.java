package com.exam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 高并发在线考试系统启动类。
 *
 * <p>技术栈：Spring Boot 3.x + MyBatis-Plus + Spring Security + JWT
 * + Redis + RabbitMQ + WebSocket + AOP</p>
 *
 * <p>{@code @MapperScan} 让 MyBatis 扫描 mapper 包，
 * 省去在每个 Mapper 接口上重复写 {@code @Mapper}。</p>
 */
@SpringBootApplication
@MapperScan("com.exam.mapper")
public class ExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamApplication.class, args);
    }
}