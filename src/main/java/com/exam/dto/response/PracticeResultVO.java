package com.exam.dto.response;

import lombok.Data;

/**
 * 练习模式「对答案」的结果。
 *
 * <p>{@code judged=false} 表示**没有自动判据**：填空题与简答题在正式考试里也是
 * 由人评的（见已知限制里的「填空自动判分」），练习模式不会为了好看而假装判对错 ——
 * 只给出参考答案与解析，让学生自己核对。</p>
 */
@Data
public class PracticeResultVO {

    private Long questionId;
    private Integer type;
    /** 学生这次的作答原文 */
    private String studentAnswer;
    /** 是否给出了自动判定（客观题 true；填空/简答 false） */
    private Boolean judged;
    /** 判定结果；judged=false 时为 null（不是 false，避免前端显示成「答错」） */
    private Boolean correct;
    /** 参考答案 */
    private String correctAnswer;
    /** 答案解析 */
    private String analysis;
}