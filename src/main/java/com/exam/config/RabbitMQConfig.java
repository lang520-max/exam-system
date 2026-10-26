package com.exam.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置（仅在 RabbitMQ 启用时加载）。
 *
 * <p><b>拓扑（含失败路径）</b></p>
 * <pre>
 *   producer ──exam.grading.exchange──▶ exam.grading.queue ──▶ GradingConsumer
 *                                            │ 失败第 1-3 次：投到 retry 队列
 *                                            │                 （TTL 5s 后 DLX 送回判分队列）
 *                                            │ 超过 3 次：basicNack(requeue=false)
 *                                            ▼
 *                                      exam.dlx ──▶ exam.grading.dlq（人工介入）
 * </pre>
 *
 * <p><b>为什么必须显式声明死信交换机与死信队列</b>：判分队列上写着
 * {@code x-dead-letter-exchange = exam.dlx}，但此前全项目<b>没有</b>声明这个交换机
 * 与对应队列。RabbitMQ 对「投到不存在的交换机」是<b>静默丢弃</b>，
 * 于是「超过重试上限后由死信兜底」只是一句注释，真出问题时消息直接消失。</p>
 */
@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {

    /** 判分队列 */
    public static final String GRADING_QUEUE = "exam.grading.queue";
    /** 判分交换机 */
    public static final String GRADING_EXCHANGE = "exam.grading.exchange";
    /** 判分路由键 */
    public static final String GRADING_ROUTING_KEY = "exam.grading";

    /** 失败重试队列（延迟队列：消息在队列里等到 TTL 到期，再由 DLX 送回判分队列） */
    public static final String GRADING_RETRY_QUEUE = "exam.grading.retry.queue";
    /** 重试路由键 */
    public static final String GRADING_RETRY_ROUTING_KEY = "exam.grading.retry";
    /** 重试延迟（毫秒）：给下游一点恢复时间，也避免毒消息打满 CPU */
    public static final int RETRY_TTL_MS = 5000;

    /** 死信交换机 */
    public static final String DEAD_LETTER_EXCHANGE = "exam.dlx";
    /** 判分死信队列（人工介入） */
    public static final String GRADING_DLQ = "exam.grading.dlq";
    /** 死信路由键 */
    public static final String GRADING_DLQ_ROUTING_KEY = "exam.grading.dead";

    /** 人工复核通知队列 */
    public static final String REVIEW_QUEUE = "exam.review.queue";
    public static final String REVIEW_EXCHANGE = "exam.review.exchange";
    public static final String REVIEW_ROUTING_KEY = "exam.review";

    // ==================== 判分主链路 ====================

    @Bean
    public Queue gradingQueue() {
        return QueueBuilder.durable(GRADING_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                // 显式写死死信路由键：不写的话会沿用原 routing key，
                // 一旦有人给判分队列加了别的绑定，死信就可能被投到别处
                .withArgument("x-dead-letter-routing-key", GRADING_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange gradingExchange() {
        return new DirectExchange(GRADING_EXCHANGE);
    }

    @Bean
    public Binding gradingBinding() {
        return BindingBuilder.bind(gradingQueue()).to(gradingExchange()).with(GRADING_ROUTING_KEY);
    }

    // ==================== 失败重试（延迟队列） ====================

    /**
     * 重试队列：没有消费者，消息在这里等 TTL 到期，然后被死信回判分队列。
     *
     * <p>为什么不用 {@code basicNack(requeue=true)} 直接重投：那会让消息<b>立刻</b>
     * 回到队首，一条注定失败的消息会形成每秒数百次的死循环，
     * 而判分消费者 {@code prefetch=1}，整条判分队列都会被它独占。</p>
     */
    @Bean
    public Queue gradingRetryQueue() {
        return QueueBuilder.durable(GRADING_RETRY_QUEUE)
                .withArgument("x-message-ttl", RETRY_TTL_MS)
                .withArgument("x-dead-letter-exchange", GRADING_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", GRADING_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding gradingRetryBinding() {
        return BindingBuilder.bind(gradingRetryQueue()).to(gradingExchange()).with(GRADING_RETRY_ROUTING_KEY);
    }

    // ==================== 死信（人工介入） ====================

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue gradingDeadLetterQueue() {
        return QueueBuilder.durable(GRADING_DLQ).build();
    }

    @Bean
    public Binding gradingDeadLetterBinding() {
        return BindingBuilder.bind(gradingDeadLetterQueue())
                .to(deadLetterExchange())
                .with(GRADING_DLQ_ROUTING_KEY);
    }

    // ==================== 复核通知 ====================

    @Bean
    public Queue reviewQueue() {
        return QueueBuilder.durable(REVIEW_QUEUE).build();
    }

    @Bean
    public DirectExchange reviewExchange() {
        return new DirectExchange(REVIEW_EXCHANGE);
    }

    @Bean
    public Binding reviewBinding() {
        return BindingBuilder.bind(reviewQueue()).to(reviewExchange()).with(REVIEW_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}