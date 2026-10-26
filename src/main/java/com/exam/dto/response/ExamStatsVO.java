package com.exam.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 考试统计
 */
@Data
public class ExamStatsVO {

    private Long examId;
    private String examTitle;
    private int totalStudents;
    private int submittedCount;
    private int passCount;
    private double passRate;
    private double avgScore;
    private int maxScore;
    private int minScore;
    private int medianScore;

    /**
     * 分数分布（按<b>得分率</b>分 5 段），供前端画直方图。
     *
     * <p>为什么按得分率而不是绝对分：不同卷子满分不同（50 / 60 / 70 / 100），
     * 用绝对分段会让「50 分满分的 45 分」和「100 分满分的 45 分」落进同一格，
     * 分布图就失去意义了。</p>
     *
     * <p>无人交卷时返回 5 个 count=0 的空桶，而不是 null ——
     * 前端可以直接照着画一个空图，不必再判空。</p>
     */
    private List<ScoreBucket> distribution = new ArrayList<>();

    /** 分数分布的一段 */
    @Data
    @AllArgsConstructor
    public static class ScoreBucket {

        /** 区间标签，例如 {@code "60-69%"} */
        private String label;

        /** 落在该段的人数 */
        private int count;

        /** 该段是否属于及格区间（前端据此着色：不及格段用红色） */
        private boolean passed;
    }
}