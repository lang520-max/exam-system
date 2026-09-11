package com.caikailang.examsystem.dto;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 题目新增 / 编辑表单。
 *
 * <p>注解只挡住「格式明显不对」的输入（空值、超长、非法枚举、越界分值）；
 * 「答案是否落在选项范围内」「多选题答案至少要两个字母」这类跨字段规则
 * 放在 {@code QuestionService} 里校验，因为需要结合题型判断。</p>
 *
 * @author 蔡开浪
 */
public class QuestionForm {

    /** 编辑时由路径变量回填，新增时为空 */
    private Long id;

    @NotBlank(message = "题型不能为空")
    @Pattern(regexp = "SINGLE|MULTIPLE|JUDGE", message = "题型只能是 SINGLE / MULTIPLE / JUDGE")
    private String type;

    @NotBlank(message = "科目不能为空")
    @Size(max = 32, message = "科目名称不能超过 32 个字符")
    private String subject;

    @Size(max = 64, message = "知识点不能超过 64 个字符")
    private String knowledgePoint;

    @NotBlank(message = "题干不能为空")
    @Size(max = 500, message = "题干不能超过 500 个字符")
    private String content;

    @Size(max = 200, message = "选项不能超过 200 个字符")
    private String optionA;

    @Size(max = 200, message = "选项不能超过 200 个字符")
    private String optionB;

    @Size(max = 200, message = "选项不能超过 200 个字符")
    private String optionC;

    @Size(max = 200, message = "选项不能超过 200 个字符")
    private String optionD;

    @NotBlank(message = "正确答案不能为空")
    @Size(max = 16, message = "正确答案不能超过 16 个字符")
    private String answer;

    @NotNull(message = "分值不能为空")
    @DecimalMin(value = "0.5", message = "分值不能小于 0.5")
    @DecimalMax(value = "100.0", message = "分值不能大于 100")
    private BigDecimal score;

    @NotBlank(message = "难度不能为空")
    @Pattern(regexp = "EASY|MEDIUM|HARD", message = "难度只能是 EASY / MEDIUM / HARD")
    private String difficulty = "MEDIUM";

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public void setKnowledgePoint(String knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
