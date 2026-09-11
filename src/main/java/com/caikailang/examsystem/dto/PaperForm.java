package com.caikailang.examsystem.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 手动组卷表单：教师从题库勾选若干道题组成试卷。
 *
 * <p>每道题在本卷中的分值取题库中的默认分值；若需要同题不同分，
 * 可在 {@code questionScores} 里按题目 ID 覆盖（可选）。</p>
 *
 * @author lang520-max
 */
public class PaperForm {

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

    @NotEmpty(message = "请至少选择一道题目")
    private List<Long> questionIds;

    /** 可选：按题目 ID 覆盖分值，key 为题目 ID、value 为该题在本卷中的分值 */
    private java.util.Map<Long, BigDecimal> questionScores;

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

    public List<Long> getQuestionIds() {
        return questionIds;
    }

    public void setQuestionIds(List<Long> questionIds) {
        this.questionIds = questionIds;
    }

    public java.util.Map<Long, BigDecimal> getQuestionScores() {
        return questionScores;
    }

    public void setQuestionScores(java.util.Map<Long, BigDecimal> questionScores) {
        this.questionScores = questionScores;
    }
}
