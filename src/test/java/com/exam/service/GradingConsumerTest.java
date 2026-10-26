package com.exam.service;

import com.exam.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 判分消费者的<b>失败分流</b>。
 *
 * <p>锁住的核心行为：失败时不能再 {@code basicNack(requeue=true)}。
 * 那会让消息立刻回到队首、再次失败、再回队首 —— 一条注定失败的毒消息
 * 每秒重投数百次，而 {@code prefetch=1} 的消费者被它独占，整条判分队列瘫痪。</p>
 *
 * <p>用 Mockito 直接驱动消费者，不需要 broker。</p>
 */
@DisplayName("判分消费者 · 失败分流")
class GradingConsumerTest {

    private final GradingEngine engine = mock(GradingEngine.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final GradingConsumer consumer = new GradingConsumer(engine, rabbitTemplate);

    private static final long DELIVERY_TAG = 7L;

    private Message messageWithRetryCount(Integer retry) {
        MessageBuilder builder = MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8));
        if (retry != null) {
            builder.setHeader(GradingConsumer.RETRY_HEADER, retry);
        }
        Message message = builder.build();
        message.getMessageProperties().setDeliveryTag(DELIVERY_TAG);
        return message;
    }

    @Test
    @DisplayName("判分成功：ack，且不碰重试与死信")
    void success_acks() throws Exception {
        consumer.onGradingTask(Map.of("recordId", 1), messageWithRetryCount(null), channel);

        verify(engine).grade(1L);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("首次失败：投到延迟重试队列并 ack（不是立即 requeue）")
    void firstFailure_goesToRetryQueueAndAcks() throws Exception {
        doThrow(new IllegalStateException("boom")).when(engine).grade(1L);

        consumer.onGradingTask(Map.of("recordId", 1), messageWithRetryCount(null), channel);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.GRADING_EXCHANGE),
                eq(RabbitMQConfig.GRADING_RETRY_ROUTING_KEY),
                any(),
                any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        // 关键回归点：绝不能再 basicNack(requeue=true)
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("重试用尽：requeue=false 进死信队列（人工介入），不再重投")
    void exhaustedRetries_goesToDeadLetterQueue() throws Exception {
        doThrow(new IllegalStateException("boom")).when(engine).grade(1L);

        consumer.onGradingTask(Map.of("recordId", 1),
                messageWithRetryCount(GradingConsumer.MAX_RETRY), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("消息体不合法：直接 ack 丢弃，不无限重投")
    void invalidTask_isDroppedWithoutGrading() throws Exception {
        consumer.onGradingTask(Map.of("nope", 1), messageWithRetryCount(null), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(engine, never()).grade(anyLong());
    }

    @Test
    @DisplayName("连重投都失败：退回原样 requeue，至少不丢消息")
    void retryPublishFailure_fallsBackToImmediateRequeue() throws Exception {
        doThrow(new IllegalStateException("boom")).when(engine).grade(1L);
        doThrow(new RuntimeException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        consumer.onGradingTask(Map.of("recordId", 1), messageWithRetryCount(null), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }
}