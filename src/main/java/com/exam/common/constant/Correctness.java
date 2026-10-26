package com.exam.common.constant;

/**
 * 单题的判定结果，对应 {@code student_answer.is_correct}。
 *
 * <p>与建表注释保持一致：0-错误 / 1-正确 / 2-部分正确。</p>
 *
 * <p><b>不要用它表达「待复核」</b>。之前代码把 {@code 2} 同时当作「待复核」，
 * 与列注释冲突，导致前端按 {@code == 1} 判断对错时，把待复核的主观题
 * 显示成「答错、红底、0 分」。待复核现在由
 * {@code graded_by IS NULL} 表达，见 {@link GradedBy}。</p>
 */
public interface Correctness {

    /** 答错 / 未作答 */
    int WRONG = 0;

    /** 答对（客观题精确匹配，或主观题拿到满分） */
    int CORRECT = 1;

    /** 部分正确（主观题拿到介于 0 与满分之间的分数） */
    int PARTIAL = 2;
}