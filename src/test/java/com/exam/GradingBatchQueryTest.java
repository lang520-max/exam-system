package com.exam;

import com.exam.common.constant.GradedBy;
import com.exam.common.constant.RecordStatus;
import com.exam.dto.response.ExamResultVO;
import com.exam.entity.ExamRecord;
import com.exam.mapper.QuestionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 判分与成绩单 · 题目改为批量查询（消除 N+1）。
 *
 * <p>用 {@code @SpyBean} 包住真实的 {@link QuestionMapper}：查询仍然走真实数据库、
 * 返回真实题目，只在外面加一层调用计数。因此这里断言的不是「mock 被调过」，
 * 而是「真代码一次批量查完，且一次 {@code selectById} 都没有」。</p>
 *
 * <p>原先这两处都是「循环里逐题 selectById」：一份 50 题的卷子，判分 50 次往返、
 * 成绩单再 50 次。这不是性能玄学，是每条 SQL 都真实发生在交卷高峰。</p>
 */
@DisplayName("判分 / 成绩单 · 题目批量查询")
class GradingBatchQueryTest extends GradingFixtureTestBase {

    @SpyBean
    private QuestionMapper questionMapper;

    @Test
    @DisplayName("判分：题目只走一次 selectBatchIds，零次 selectById")
    void gradingQueriesQuestionsInBatch() {
        gradingEngine.grade(recordId);

        verify(questionMapper, times(1)).selectBatchIds(anyList());
        verify(questionMapper, never()).selectById(any());

        // 批量查询不改变结果口径：客观题按试卷快照（8/6 分）判，主观题待复核
        assertThat(answerOf(objectiveQuestionId).getScore()).isEqualTo(OBJECTIVE_FULL);
        assertThat(answerOf(objectiveQuestionId).getGradedBy()).isEqualTo(GradedBy.AUTO);
        ExamRecord record = recordMapper.selectById(recordId);
        assertThat(record.getScore()).isEqualTo(OBJECTIVE_FULL);
        assertThat(record.getStatus()).isEqualTo(RecordStatus.PENDING_GRADE);
    }

    @Test
    @DisplayName("成绩单：题目只走一次 selectBatchIds，零次 selectById")
    void getResultQueriesQuestionsInBatch() {
        // 先判一遍分，让成绩单处于「客观题已判分、主观题待复核」的真实状态；
        // 随后清空调用记录，只统计成绩单这一次的查询次数
        gradingEngine.grade(recordId);
        clearInvocations(questionMapper);

        ExamResultVO result = examService.getResult(recordId, USER_ID);

        verify(questionMapper, times(1)).selectBatchIds(anyList());
        verify(questionMapper, never()).selectById(any());

        // 数据口径不变：4 题全在、2 道主观题待复核、满分取自卷面快照
        assertThat(result.getAnswers()).hasSize(4);
        assertThat(result.getTotalCount()).isEqualTo(4);
        assertThat(result.getPendingReviewCount()).isEqualTo(2);
        assertThat(result.getAnswers().stream()
                .filter(a -> objectiveQuestionId.equals(a.getQuestionId()))
                .findFirst().orElseThrow().getScore())
                .as("展示满分必须取试卷快照的 8 分，而不是题库默认的 3 分")
                .isEqualTo(OBJECTIVE_FULL);
        assertThat(result.getAnswers().stream()
                .filter(a -> subjectiveQuestionId.equals(a.getQuestionId()))
                .findFirst().orElseThrow().getPendingReview())
                .as("主观题仍是「待复核」而不是「答错」")
                .isTrue();
    }
}