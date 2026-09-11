package com.caikailang.examsystem.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单题判分结果。
 *
 * <p>它只承载「判分」这件事需要的数据：题目 ID、学生答案、正确答案、本题满分、实际得分、对错。
 * 不携带题干/选项等内容字段，因此 {@code GradeCalculator} 完全可以脱离数据库做纯单元测试。</p>
 *
 * @author lang520-max
 */
public class GradeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long questionId;
    /** 题型：SINGLE / MULTIPLE / JUDGE，便于测试与日志定位 */
    private String type;
    /** 学生作答（规范化之后的值，未作答为 null） */
    private String studentAnswer;
    /** 正确答案快照 */
    private String correctAnswer;
    /** 本题满分快照 */
    private BigDecimal fullScore;
    /** 本题实际得分 */
    private BigDecimal earnedScore;
    /** 是否答对 */
    private boolean correct;

    public GradeItem() {
    }

    public GradeItem(Long questionId, String type, String studentAnswer, String correctAnswer,
                     BigDecimal fullScore, BigDecimal earnedScore, boolean correct) {
        this.questionId = questionId;
        this.type = type;
        this.studentAnswer = studentAnswer;
        this.correctAnswer = correctAnswer;
        this.fullScore = fullScore;
        this.earnedScore = earnedScore;
        this.correct = correct;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStudentAnswer() {
        return studentAnswer;
    }

    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public BigDecimal getFullScore() {
        return fullScore;
    }

    public void setFullScore(BigDecimal fullScore) {
        this.fullScore = fullScore;
    }

    public BigDecimal getEarnedScore() {
        return earnedScore;
    }

    public void setEarnedScore(BigDecimal earnedScore) {
        this.earnedScore = earnedScore;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }
}
