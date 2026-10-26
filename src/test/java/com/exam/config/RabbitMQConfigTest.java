package com.exam.config;

import com.exam.service.GradingConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RabbitMQ 拓扑声明。
 *
 * <p>不需要 broker：直接调用配置类的 {@code @Bean} 方法，检查返回的
 * Queue / Exchange / Binding 参数。要锁住的是一件事 ——
 * <b>判分队列上引用的死信交换机必须真的存在</b>。
 * 此前 {@code x-dead-letter-exchange = exam.dlx} 只是被引用，
 * 全项目没有声明 {@code exam.dlx} 与死信队列，而 RabbitMQ 对
 * 「投到不存在的交换机」是静默丢弃：真出问题时消息直接消失。</p>
 */
@DisplayName("RabbitMQ 拓扑 · 失败路径")
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    @DisplayName("判分队列显式声明了死信交换机与死信路由键")
    void gradingQueue_deadLettersToDeclaredDlx() {
        Queue queue = config.gradingQueue();

        assertThat(queue.getName()).isEqualTo(RabbitMQConfig.GRADING_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.GRADING_DLQ_ROUTING_KEY);
    }

    @Test
    @DisplayName("死信交换机、死信队列与绑定都真的声明了（不再是悬空引用）")
    void deadLetterTopology_isDeclared() {
        assertThat(config.deadLetterExchange().getName()).isEqualTo(RabbitMQConfig.DEAD_LETTER_EXCHANGE);
        assertThat(config.gradingDeadLetterQueue().getName()).isEqualTo(RabbitMQConfig.GRADING_DLQ);

        Binding binding = config.gradingDeadLetterBinding();
        assertThat(binding.getExchange()).isEqualTo(RabbitMQConfig.DEAD_LETTER_EXCHANGE);
        assertThat(binding.getDestination()).isEqualTo(RabbitMQConfig.GRADING_DLQ);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitMQConfig.GRADING_DLQ_ROUTING_KEY);
    }

    @Test
    @DisplayName("重试队列：TTL 到期后由死信送回判分队列（延迟重试，不是立刻重投）")
    void retryQueue_delaysBackToGradingQueue() {
        Queue retry = config.gradingRetryQueue();

        assertThat(retry.getName()).isEqualTo(RabbitMQConfig.GRADING_RETRY_QUEUE);
        assertThat(retry.getArguments())
                .containsEntry("x-message-ttl", RabbitMQConfig.RETRY_TTL_MS)
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.GRADING_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.GRADING_ROUTING_KEY);

        Binding binding = config.gradingRetryBinding();
        assertThat(binding.getExchange()).isEqualTo(RabbitMQConfig.GRADING_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitMQConfig.GRADING_RETRY_ROUTING_KEY);
    }

    @Test
    @DisplayName("重试上限是个正整数（否则要么不重试、要么退化成死循环）")
    void retryCap_isPositive() {
        assertThat(GradingConsumer.MAX_RETRY).isGreaterThanOrEqualTo(1);
        assertThat(RabbitMQConfig.RETRY_TTL_MS).isGreaterThan(0);
    }
}