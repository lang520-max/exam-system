package com.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 试卷题目快照项 —— 记录「这份卷子考哪道题、该题在卷面上值多少分」。
 *
 * <p>题库里题目的分值后续可能被教师改动，但已经组好的试卷不应该跟着变，
 * 因此分值随题目一起快照进试卷。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSnapshot {

    /** 题目 ID */
    private Long questionId;

    /** 该题在本卷中的分值 */
    private Integer score;
}