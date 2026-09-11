package com.caikailang.examsystem.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 试卷与题目的关联（一份试卷的第 {@code sortOrder} 道题是哪道题、值多少分）。
 *
 * <p>关键设计：这里冗余保存了 {@code score}（该题在本卷中的分值快照）。
 * 教师在题库里调整某题分值后，已经考过的试卷与学生历史成绩不会因此被改写，
 * 这与订单里「成交价快照」是同一个思路。</p>
 *
 * <p>数据库上有 {@code UNIQUE(paper_id, question_id)}，保证同一道题不会在一份试卷里出现两次。</p>
 *
 * @author lang520-max
 */
public class ExamPaperQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long paperId;
    private Long questionId;
    /** 该题在本卷中的分值（快照） */
    private BigDecimal score;
    /** 题目在试卷中的顺序，从 1 开始 */
    private Integer sortOrder;

    /* ---------- 以下为关联查询带出的题目快照字段，不落库 ---------- */

    private String type;
    private String subject;
    private String knowledgePoint;
    private String content;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String answer;
    private String difficulty;

    /** 题型中文名，页面直接展示 */
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

    public boolean isMultiple() {
        return Question.TYPE_MULTIPLE.equals(type);
    }

    /**
     * 转成判分用的 {@link Question}：分值取本卷的快照，而不是题库里的当前分值。
     *
     * <p>这样交卷判分只需一次「取试卷题目」的查询，不必再按题目 ID 回表，
     * 也保证了「题库改分不回写历史试卷」。</p>
     */
    public Question toQuestion() {
        Question question = new Question();
        question.setId(questionId);
        question.setType(type);
        question.setSubject(subject);
        question.setKnowledgePoint(knowledgePoint);
        question.setContent(content);
        question.setOptionA(optionA);
        question.setOptionB(optionB);
        question.setOptionC(optionC);
        question.setOptionD(optionD);
        question.setAnswer(answer);
        question.setScore(score);
        question.setDifficulty(difficulty);
        return question;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPaperId() {
        return paperId;
    }

    public void setPaperId(Long paperId) {
        this.paperId = paperId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
