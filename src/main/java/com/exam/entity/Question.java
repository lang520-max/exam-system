package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目
 */
@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属题库 */
    private Long bankId;

    /** 0-单选 1-多选 2-判断 3-填空 4-简答 */
    private Integer type;

    /** 1-简单 2-中等 3-困难 */
    private Integer difficulty;

    /** 题目内容（支持富文本） */
    private String content;

    /** 选项 JSON: [{"label":"A","content":"xxx"},...] */
    private String options;

    /** 标准答案 */
    private String answer;

    /** 分值 */
    private Integer score;

    /** 题目解析 */
    private String analysis;

    /**
     * 知识点/专题（V4）：如「一元函数积分学」「语法」。
     *
     * <p>专项训练卷与自由练习的「专题」筛选都靠它。允许为空 ——
     * 抽题的硬约束始终是题型与难度，知识点是**进一步收窄**用的标签。</p>
     */
    private String category;

    /** 0-禁用 1-启用 */
    private Integer status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}