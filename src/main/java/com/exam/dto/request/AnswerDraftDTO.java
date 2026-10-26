package com.exam.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * 答题过程中的「暂存答案」请求体。
 *
 * <p>与交卷（{@link ExamSubmitDTO}）的区别：暂存**只写作答文本**，
 * 不改变考试状态、不触发判分，因此不需要 {@code recordId}（它在路径里）
 * 也不需要幂等键 —— 重复暂存同一道题的结果是幂等的。</p>
 *
 * <p>前端只传**有变化的题**即可，不必每次全量上传。</p>
 */
@Data
public class AnswerDraftDTO {

    /** questionId → 作答内容 */
    private Map<Long, String> answers;
}