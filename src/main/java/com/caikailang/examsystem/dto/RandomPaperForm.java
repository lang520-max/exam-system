package com.caikailang.examsystem.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 随机组卷表单：按「科目 + 题型 + 数量 + 每题分值」自动抽题。
 *
 * <p>示例：单选题 10 道 × 4 分 + 多选题 5 道 × 8 分 + 判断题 5 道 × 4 分 = 100 分。</p>
 *
 * <p>{@code rules} 上的 {@code @Valid} 会级联校验集合里的每个 {@link RandomRule}，
 * 保证「数量为 0」「分值为负」这类脏数据在进入 Service 之前就被拦下。</p>
 *
 * @author lang520-max
 */
public class RandomPaperForm {

    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 100, message = "试卷标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "科目不能为空")
    @Size(max = 32, message = "科目名称不能超过 32 个字符")
    private String subject;

    @NotNull(message = "考试时长不能为空")
    @Min(value = 1, message = "考试时长至少 1 分钟")
    @Max(value = 600, message = "考试时长不能超过 600 分钟")
    private Integer durationMinutes;

    @NotNull(message = "及格分不能为空")
    @DecimalMin(value = "0.0", message = "及格分不能为负数")
    private BigDecimal passScore;

    @NotEmpty(message = "请至少配置一条抽题规则")
    @Valid
    private List<RandomRule> rules;

    /** 单条抽题规则 */
    public static class RandomRule {

        @NotBlank(message = "抽题规则的题型不能为空")
        @Pattern(regexp = "SINGLE|MULTIPLE|JUDGE", message = "题型只能是 SINGLE / MULTIPLE / JUDGE")
        private String type;

        @NotNull(message = "抽题数量不能为空")
        @Min(value = 1, message = "抽题数量至少 1 道")
        @Max(value = 50, message = "单次抽题数量不能超过 50 道")
        private Integer count;

        @NotNull(message = "每题分值不能为空")
        @DecimalMin(value = "0.5", message = "每题分值不能小于 0.5")
        @DecimalMax(value = "100.0", message = "每题分值不能大于 100")
        private BigDecimal score;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public BigDecimal getScore() {
            return score;
        }

        public void setScore(BigDecimal score) {
            this.score = score;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public BigDecimal getPassScore() {
        return passScore;
    }

    public void setPassScore(BigDecimal passScore) {
        this.passScore = passScore;
    }

    public List<RandomRule> getRules() {
        return rules;
    }

    public void setRules(List<RandomRule> rules) {
        this.rules = rules;
    }
}
