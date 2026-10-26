package com.exam.common.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把一段动作延后到<b>事务提交之后</b>执行。
 *
 * <p><b>为什么需要它</b>：在 {@code @Transactional} 方法里直接发 MQ 消息 / 调外部接口，
 * 消息会在事务提交前就离开本进程。消费者在另一个线程、另一个连接、另一个事务里读取，
 * 很可能读到的还是提交前的旧数据 —— 于是按旧状态做了「幂等跳过」、
 * 把「跳过」当成功消费掉，那一刻的错误再也无法回滚。</p>
 *
 * <p>代价与取舍：提交后才执行，意味着如果提交本身失败，动作不会发生（这正是我们要的）；
 * 反过来，提交成功但动作失败时不会有回滚，调用方需要自带补偿手段
 * （本项目是「待判分补偿任务」，见 {@code ExamService.sweepStuckPendingGradeRecords}）。</p>
 *
 * <p>没有活跃事务同步时（例如被非事务方法直接调用）立即执行，
 * 因此它不会悄悄吞掉动作。</p>
 */
public final class AfterCommitExecutor {

    private AfterCommitExecutor() {
    }

    /**
     * 提交后执行；当前没有事务时立即执行。
     *
     * @param action 要在提交后执行的动作；执行时的异常由 action 自己负责捕获，
     *               否则会以「提交阶段异常」的形式冒出到调用方
     */
    public static void afterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}