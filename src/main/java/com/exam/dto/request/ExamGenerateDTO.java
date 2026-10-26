package com.exam.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 组卷参数
 */
@Data
public class ExamGenerateDTO {

    @NotBlank(message = "考试标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "请选择题库")
    private Long bankId;

    /**
     * 考试时长（分钟）。
     *
     * <p>{@code @Min(1)} 不是装饰：duration 会参与「截止时刻 = 开考时间 + 时长」
     * 的计算，传 0 或负数会让考试一开始就过期。</p>
     */
    @NotNull(message = "请设置考试时长")
    @Min(value = 1, message = "考试时长必须大于 0 分钟")
    private Integer duration;

    private Integer passScore;

    /**
     * 试卷类型（V4）：模拟卷 / 专项训练。留空按「模拟卷」处理。
     *
     * <p>只影响展示与分类，不参与抽题 —— 抽题的约束在 rules 里。</p>
     */
    private String paperType;

    @Valid
    @NotNull(message = "请配置组卷规则")
    private List<RuleItem> rules;

    @Data
    public static class RuleItem {
        /** 题型：0-单选 1-多选 2-判断 3-填空 4-简答 */
        @NotNull(message = "题型不能为空")
        private Integer type;

        /** 题目数量 */
        @NotNull(message = "题目数量不能为空")
        @Min(value = 1, message = "题目数量必须大于 0")
        private Integer count;

        /** 每题分值 */
        @NotNull(message = "每题分值不能为空")
        @Min(value = 1, message = "每题分值必须大于 0")
        private Integer score;

        /**
         * 知识点/专题（V4），可选：填了就只从这个专题里抽题。
         *
         * <p>这是「一元函数积分学专项训练」这类卷子的做法。注意一个刻意的取舍：
         * 指定了专题时**不会跨专题借题**（见 ExamRuleService），题量不足会直接报错 ——
         * 借题会让「专项」名不副实，而教师看到的会是一份混了别的章节的卷子。</p>
         */
        private String category;
    }
}