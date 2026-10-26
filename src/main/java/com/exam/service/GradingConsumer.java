package com.exam.service;

import com.exam.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 异步判分消费者 —— RabbitMQ 的触发入口。
 *
 * <p>本类<b>只负责消息收发与 ack 语义</b>，真正的判分规则在 {@link GradingEngine}。
 * 这样拆分的收益是：判分逻辑可以被单元测试直接调用，不必为了跑测试而启动 RabbitMQ。</p>
 *
 * <p><b>失败处理（此前是无限重投的死循环）</b></p>
 * <ol>
 *   <li>判分失败 → 第 1~{@value #MAX_RETRY} 次投到<b>延迟重试队列</b>
 *       （{@link RabbitMQConfig#GRADING_RETRY_QUEUE}，TTL 到期后由死信回判分队列），
 *       然后 ack 原消息；</li>
 *   <li>超过上限 → {@code basicNack(requeue=false)}，经 {@code exam.dlx}
 *       进死信队列 {@link RabbitMQConfig#GRADING_DLQ}，等人工介入；</li>
 *   <li>不吞异常、不静默丢消息；判分本身幂等（{@link GradingEngine#grade}
 *       只处理「待判分」状态），重复投递不会二次算分。</li>
 * </ol>
 *
 * <p>此前是「失败就 {@code basicNack(requeue=true)}」：消息立刻回到队首、
 * 再次失败、再回队首，一条因数据问题注定失败的「毒消息」会每秒重投数百次，
 * 而消费者 {@code prefetch=1}，整条判分队列都被它占住。类注释里
 * 「超过重试上限后由死信兜底」当时只是承诺，并没有实现。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
@RabbitListener(queues = RabbitMQConfig.GRADING_QUEUE, ackMode = "MANUAL")
public class GradingConsumer {

    /** 失败后最多重投几次（首次投递不计入），超过就进死信队列 */
    public static final int MAX_RETRY = 3;

    /** 自定义重试计数头，随消息在「判分队列 ↔ 重试队列」之间往返 */
    public static final String RETRY_HEADER = "x-grading-retry";

    private final GradingEngine gradingEngine;
    private final RabbitTemplate rabbitTemplate;

    public GradingConsumer(GradingEngine gradingEngine, RabbitTemplate rabbitTemplate) {
        this.gradingEngine = gradingEngine;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitHandler
    public void onGradingTask(Map<String, Object> task, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long recordId = extractRecordId(task);

        if (recordId == null) {
            // 消息体本身不合法，重投多少次都不会成功 → 直接丢弃，避免死循环
            log.warn("收到无效判分任务，直接丢弃: {}", task);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            gradingEngine.grade(recordId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleFailure(task, message, channel, deliveryTag, recordId, e);
        }
    }

    /** 失败分流：先延迟重试，超过上限再进死信队列 */
    private void handleFailure(Map<String, Object> task, Message message, Channel channel,
                               long deliveryTag, Long recordId, Exception cause) throws Exception {
        int retry = retryCountOf(message);

        if (retry >= MAX_RETRY) {
            log.error("判分连续失败 {} 次，转入死信队列等待人工处理: recordId={}", retry, recordId, cause);
            // requeue=false：由队列上的 x-dead-letter-exchange 送进 DLQ
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        log.warn("判分失败（第 {}/{} 次），{}ms 后重投: recordId={}",
                retry + 1, MAX_RETRY, RabbitMQConfig.RETRY_TTL_MS, recordId, cause);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.GRADING_EXCHANGE,
                    RabbitMQConfig.GRADING_RETRY_ROUTING_KEY,
                    task,
                    msg -> {
                        msg.getMessageProperties().setHeader(RETRY_HEADER, retry + 1);
                        return msg;
                    });
            // 重投成功才 ack 原消息（顺序不能反：先 ack 再投，投失败就真的丢了）
            channel.basicAck(deliveryTag, false);
        } catch (Exception publishFailure) {
            // 连重投都失败：退回「原样重投」，至少不丢消息
            log.error("重投到延迟队列失败，改为立即重回队列: recordId={}", recordId, publishFailure);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private int retryCountOf(Message message) {
        Object raw = message.getMessageProperties().getHeader(RETRY_HEADER);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Long extractRecordId(Map<String, Object> task) {
        if (task == null) {
            return null;
        }
        Object value = task.get("recordId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}