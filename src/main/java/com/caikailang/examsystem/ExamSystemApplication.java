package com.caikailang.examsystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 在线考试 / 题库系统启动类。
 *
 * <p>启动后访问 <a href="http://localhost:8080">http://localhost:8080</a>，
 * 演示账号见 README 或登录页提示。</p>
 *
 * <p>{@code @MapperScan} 让 MyBatis 扫描 mapper 包，省去在每个接口上写 {@code @Mapper}。</p>
 *
 * @author 蔡开浪
 */
@SpringBootApplication
@MapperScan("com.caikailang.examsystem.mapper")
public class ExamSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamSystemApplication.class, args);
    }
}
