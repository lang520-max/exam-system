package com.exam.common;

import com.exam.common.utils.AfterCommitExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「提交后执行」的语义。
 *
 * <p>这是 S1 那个「答卷永久卡在待判分」缺陷的根因所在：判分消息必须在事务
 * <b>提交之后</b>才离开本进程。这个测试不需要 Spring 上下文，
 * 手工激活事务同步即可验证「提交前不发、提交后才发」。</p>
 */
@DisplayName("提交后执行 · AfterCommitExecutor")
class AfterCommitExecutorTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("没有事务时立即执行（不静默吞掉动作）")
    void withoutTransaction_runsImmediately() {
        AtomicInteger ran = new AtomicInteger();
        AfterCommitExecutor.afterCommit(ran::incrementAndGet);
        assertThat(ran.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("有事务时提交前不执行、提交后才执行")
    void withTransaction_runsOnlyAfterCommit() {
        AtomicInteger ran = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        try {
            AfterCommitExecutor.afterCommit(ran::incrementAndGet);

            assertThat(ran.get())
                    .as("提交之前绝不能被调用（否则消费者可能读到旧状态并跳过判分）")
                    .isZero();

            // 模拟事务提交：Spring 在提交时会回调所有已注册的同步器
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            assertThat(ran.get()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}