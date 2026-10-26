package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生答案
 */
@Data
@TableName("student_answer")
public class StudentAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long questionId;

    /** 学生答案 */
    private String answer;

    /** 本题得分 */
    private Integer score;

    /** 0-错误 1-正确 2-部分正确（注意：不使用 2 表示「待复核」，那由 gradedBy=null 表达） */
    private Integer isCorrect;

    /**
     * 评分来源：null-待复核 / AUTO-客观题自动 / AI-模型评分 / TEACHER-教师复核。
     *
     * <p>原先用 {@code is_correct = 2} 兼职表示「待复核」，与列注释
     * 「2-部分正确」冲突，导致成绩单把主观题显示成「答错」。现在拆开。</p>
     */
    private String gradedBy;

    /** AI 给出的评分理由（学生可见，用于解释得分） */
    private String aiReason;

    /** AI 评分所用模型 */
    private String aiModel;

    /** 人工复核人（仅 TEACHER 评分时写入） */
    private Long reviewedBy;

    /** 该题完成评分的时间（AUTO / AI / TEACHER 都会写） */
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}