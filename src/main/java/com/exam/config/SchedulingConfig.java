package com.exam.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 *
 * <p>目前只有一个任务：{@code ExamService.sweepTimeoutRecords()} 清理超时未交卷的记录。</p>
 *
 * <p><b>为什么单独抽一个配置类并加开关</b>：测试档必须关掉它。
 * 否则一个测试刚开考、还没走到交卷断言，定时任务就可能把这条记录扫成「超时」，
 * 结果测试随机失败——这类「跟时钟赛跑」的不稳定测试极难排查。
 * 生产与 dev 档默认开启（{@code matchIfMissing = true}）。</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.exam.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}