package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试
 */
@Data
@TableName("exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    /** 所属题库 */
    private Long bankId;

    /** 考试时长(分钟) */
    private Integer duration;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /**
     * 试卷类型（V4）：模拟卷 / 专项训练。
     *
     * <p>与标题里的专题名互补：标题承载「考什么」（一元函数积分学），
     * 类型承载「这是哪一类卷子」。默认「模拟卷」，老数据与不带该字段的导入不受影响。</p>
     */
    private String paperType;

    /** 组卷规则 JSON */
    private String ruleConfig;

    /**
     * 试卷题目快照 JSON —— 组卷时抽中的题目 ID 与卷面分值。
     *
     * <p>形如 {@code [{"questionId":3,"score":10},{"questionId":7,"score":5}]}。</p>
     *
     * <p><b>为什么必须落库快照</b>：随机组卷每次抽题结果都不同。如果开考时再去题库重新抽一次，
     * 学生会拿到与组卷时不同的题；而判分时若从题库全量取题，又会把没考到的题也算进成绩单。
     * 快照让「这份卷子到底考哪几道题、每题多少分」在发布那一刻就固定下来，
     * 与订单里的成交明细是同一个思路。</p>
     */
    private String questionSnapshot;

    /** 考试开始时间 */
    private LocalDateTime startTime;

    /** 考试结束时间 */
    private LocalDateTime endTime;

    /** 0-未发布 1-进行中 2-已结束 */
    private Integer status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public boolean isPublished() {
        return status != null && status >= 1;
    }

    public boolean isInProgress() {
        return status != null && status == 1;
    }

    public boolean isFinished() {
        return status != null && status == 2;
    }
}