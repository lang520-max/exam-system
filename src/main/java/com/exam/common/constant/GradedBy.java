package com.exam.common.constant;

/**
 * 答案的评分来源。
 *
 * <p>原先用 {@code is_correct = 2} 兼职表示「待复核」，但那一列的注释写的是
 * 「2-部分正确」，两套语义打架：前端按 {@code isCorrect == 1} 判断对错，
 * 于是主观题被显示成<b>答错、红底、0 分</b>。现在把「由谁评分」独立出来。</p>
 *
 * <p>{@code null}（不写库）即表示该题<b>尚未评分</b>，也就是「待复核」。</p>
 */
public interface GradedBy {

    /** 客观题按标准答案自动判分 */
    String AUTO = "AUTO";

    /** 由 AI 模型评分 */
    String AI = "AI";

    /** 由教师复核评分 */
    String TEACHER = "TEACHER";
}