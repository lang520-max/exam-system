package com.caikailang.examsystem.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 答题明细：一次考试中某一道题的作答与判分结果。
 *
 * <p>{@code correctAnswer} 与 {@code score} 都是判分那一刻的快照：
 * 教师事后修改题库答案或分值，不会影响已经判完的历史答卷，错题回顾依旧准确。</p>
 *
 * <p>数据库唯一约束 {@code UNIQUE(record_id, question_id)}：一道题在同一个考试记录里
 * 只允许留一条作答，是防重复交卷的第二道保险。</p>
 *
 * @author lang520-max
 */
public class ExamAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long recordId;
    private Long questionId;
    /** 学生作答（未作答为 null） */
    private String studentAnswer;
    /** 正确答案快照 */
    private String correctAnswer;
    /** 该题满分快照 */
    private BigDecimal score;
    /** 实际得分 */
    private BigDecimal earnedScore;
    /** 是否答对 */
    private Boolean correct;
    private Date createdAt;

    /* ---------- 以下为关联查询带出的题目内容，供错题回顾展示 ---------- */

    private String type;
    private String subject;
    private String knowledgePoint;
    private String content;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;

    public String getTypeText() {
        if (Question.TYPE_SINGLE.equals(type)) {
            return "单选题";
        }
        if (Question.TYPE_MULTIPLE.equals(type)) {
            return "多选题";
        }
        if (Question.TYPE_JUDGE.equals(type)) {
            return "判断题";
        }
        return "未知题型";
    }

    /** 学生答案的展示文本：未作答时给出明确提示，而不是空白 */
    public String getStudentAnswerText() {
        return studentAnswer == null || studentAnswer.trim().isEmpty() ? "未作答" : studentAnswer;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
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

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getEarnedScore() {
        return earnedScore;
    }

    public void setEarnedScore(BigDecimal earnedScore) {
        this.earnedScore = earnedScore;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
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
}
