package com.exam;

import com.exam.common.constant.Correctness;
import com.exam.common.constant.GradedBy;
import com.exam.common.constant.RecordStatus;
import com.exam.entity.ExamRecord;
import com.exam.entity.StudentAnswer;
import com.exam.service.AiGradingService;
import com.exam.service.GradingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 判分 · AI 调用必须发生在事务之外。
 *
 * <p><b>锁住的行为</b>：{@code app.ai.timeout-seconds} 默认 25 秒，主观题还是<b>串行</b>评分。
 * 如果 AI 调用落在判分事务里，一份主观题多的答卷会占着数据库连接好几分钟；
 * 交卷高峰时 HikariCP 连接池被占满，判分吞吐直接崩塌。
 * 因此这里在假 AI 内部直接断言「当前线程没有活动事务」——
 * 这是最朴素也最可靠的做法：它不依赖任何实现细节，只要 AI 是在事务里被调用的，用例立刻红。</p>
 *
 * <p>同时守住降级语义：AI 未启用或调用失败时，该题必须留在「待复核」({@code graded_by IS NULL})，
 * 记录停在「待判分」，绝不写 0 分 —— 「未评分」和「答错」不是一回事。</p>
 */
@DisplayName("判分 · AI 调用在事务之外")
class GradingAiTransactionBoundaryTest extends GradingFixtureTestBase {

    /** 两道主观题拿到的分刻意不同，用来验证批量写分是多行对多行的，没有串行 */
    private static final int FIRST_SUBJECTIVE_SCORE = 9;
    private static final int SECOND_SUBJECTIVE_SCORE = 3;

    @MockBean
    private AiGradingService aiGradingService;

    private final AtomicInteger aiCallCount = new AtomicInteger();

    /** 假 AI：先断言「没有活动事务」，再按题面返回不同分数 */
    private AiGradingService.AiScore answeringOutsideTransaction(String questionContent) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("AI 调用期间不应存在活动事务：一次调用最长 app.ai.timeout-seconds 秒，"
                        + "N 道主观题还是串行，包在事务里会长时间占住数据库连接")
                .isFalse();
        aiCallCount.incrementAndGet();
        int score = "主观题一".equals(questionContent) ? FIRST_SUBJECTIVE_SCORE : SECOND_SUBJECTIVE_SCORE;
        return new AiGradingService.AiScore(score, Correctness.PARTIAL, "理由：" + questionContent, "test-model");
    }

    private void stubAi(org.mockito.stubbing.Answer<AiGradingService.AiScore> answer) {
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(anyString(), anyString(), anyInt(), anyString())).thenAnswer(answer);
    }

    private void assertTestItselfHasNoTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("用例自身必须不在事务里，否则「AI 在事务之外」这条断言恒真、毫无意义")
                .isFalse();
    }

    @Test
    @DisplayName("AI 在事务之外被调用，两题的分数各自落库、记录直接出分")
    void aiCallHappensOutsideTransaction() {
        assertTestItselfHasNoTransaction();
        stubAi(invocation -> answeringOutsideTransaction(invocation.getArgument(0)));

        GradingEngine.GradeSummary summary = gradingEngine.grade(recordId);

        assertThat(aiCallCount).hasValue(2);
        assertThat(summary.pendingReviewCount()).isZero();
        assertThat(summary.aiGradedCount()).isEqualTo(2);
        assertThat(summary.objectiveScore()).isEqualTo(OBJECTIVE_FULL);
        assertThat(summary.aiScore()).isEqualTo(FIRST_SUBJECTIVE_SCORE + SECOND_SUBJECTIVE_SCORE);

        StudentAnswer subjective = answerOf(subjectiveQuestionId);
        assertThat(subjective.getScore()).isEqualTo(FIRST_SUBJECTIVE_SCORE);
        assertThat(subjective.getGradedBy()).isEqualTo(GradedBy.AI);
        assertThat(subjective.getIsCorrect()).isEqualTo(Correctness.PARTIAL);
        assertThat(subjective.getAiReason()).isEqualTo("理由：主观题一");
        assertThat(subjective.getAiModel()).isEqualTo("test-model");

        // 第二题的分数不能被第一题串掉 —— 批量写分用的是 CASE id WHEN ...，
        // 一旦行与分数错位，这里立刻能看出来
        StudentAnswer subjective2 = answerOf(subjective2QuestionId);
        assertThat(subjective2.getScore()).isEqualTo(SECOND_SUBJECTIVE_SCORE);
        assertThat(subjective2.getAiReason()).isEqualTo("理由：主观题二");

        // 客观题按试卷快照判分（题库默认值是 3，卷面是 8），且不受 AI 影响
        StudentAnswer objective = answerOf(objectiveQuestionId);
        assertThat(objective.getScore()).isEqualTo(OBJECTIVE_FULL);
        assertThat(objective.getGradedBy()).isEqualTo(GradedBy.AUTO);

        ExamRecord record = recordMapper.selectById(recordId);
        assertThat(record.getScore())
                .isEqualTo(OBJECTIVE_FULL + FIRST_SUBJECTIVE_SCORE + SECOND_SUBJECTIVE_SCORE);
        assertThat(record.getStatus()).isEqualTo(RecordStatus.GRADED);
    }

    @Test
    @DisplayName("AI 调用失败：主观题仍待复核、记录仍待判分，客观题分数不受影响")
    void aiFailureKeepsPendingReviewAndPendingGrade() {
        assertTestItselfHasNoTransaction();
        stubAi(invocation -> {
            // 失败路径同样不能有事务：超时/限流是最耗时的一种情况，更不能占着连接
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("AI 调用（哪怕失败）也必须在事务之外")
                    .isFalse();
            aiCallCount.incrementAndGet();
            return null;   // 超时 / 限流 / 响应畸形 —— 服务一律返回 null
        });

        GradingEngine.GradeSummary summary = gradingEngine.grade(recordId);

        assertThat(aiCallCount).hasValue(2);
        assertThat(summary.pendingReviewCount()).isEqualTo(2);
        assertThat(summary.aiGradedCount()).isZero();
        assertThat(summary.totalScore()).isEqualTo(OBJECTIVE_FULL);

        for (Long questionId : new Long[]{subjectiveQuestionId, subjective2QuestionId}) {
            StudentAnswer subjective = answerOf(questionId);
            assertThat(subjective.getGradedBy())
                    .as("未评分必须是 NULL，而不是写入一个 0 分结论")
                    .isNull();
            assertThat(subjective.getScore()).isZero();
            assertThat(subjective.getAiReason()).isNull();
        }

        ExamRecord record = recordMapper.selectById(recordId);
        assertThat(record.getStatus()).isEqualTo(RecordStatus.PENDING_GRADE);
        assertThat(record.getScore())
                .as("客观题的 8 分必须保留，不能被 AI 失败连带清零")
                .isEqualTo(OBJECTIVE_FULL);
    }

    @Test
    @DisplayName("AI 未启用：一次请求都不发，主观题保持待复核")
    void aiDisabledDoesNotCallAi() {
        when(aiGradingService.isEnabled()).thenReturn(false);

        GradingEngine.GradeSummary summary = gradingEngine.grade(recordId);

        verify(aiGradingService, never()).grade(anyString(), anyString(), anyInt(), anyString());
        assertThat(summary.pendingReviewCount()).isEqualTo(2);
        assertThat(answerOf(subjectiveQuestionId).getGradedBy()).isNull();
        assertThat(recordMapper.selectById(recordId).getStatus()).isEqualTo(RecordStatus.PENDING_GRADE);
    }

    @Test
    @DisplayName("重复判分幂等：第二次调用直接跳过，不会二次调用 AI")
    void repeatedGradingIsIdempotent() {
        stubAi(invocation -> answeringOutsideTransaction(invocation.getArgument(0)));

        gradingEngine.grade(recordId);
        GradingEngine.GradeSummary second = gradingEngine.grade(recordId);

        assertThat(second).as("只有「待判分」才处理，已出分的记录重复投递应跳过").isNull();
        assertThat(aiCallCount).hasValue(2);
        assertThat(recordMapper.selectById(recordId).getScore())
                .isEqualTo(OBJECTIVE_FULL + FIRST_SUBJECTIVE_SCORE + SECOND_SUBJECTIVE_SCORE);
    }
}