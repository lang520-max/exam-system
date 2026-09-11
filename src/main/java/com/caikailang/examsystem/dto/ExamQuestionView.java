package com.caikailang.examsystem.dto;

import com.caikailang.examsystem.domain.ExamPaperQuestion;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 考试进行中下发给学生的题目视图。
 *
 * <p><b>刻意不包含 {@code answer} 字段。</b>直接把题库实体返回给前端，
 * 学生打开浏览器开发者工具就能在响应里看到标准答案，考试形同虚设。
 * 用独立的视图对象做「字段级最小暴露」，正确答案只在交卷后的错题回顾里出现。</p>
 *
 * @author lang520-max
 */
public class ExamQuestionView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题号，从 1 开始 */
    private int sortOrder;
    private Long questionId;
    private String type;
    private String typeText;
    private String knowledgePoint;
    private String content;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    /** 本题分值，前端用来提示「本题 4 分」 */
    private BigDecimal score;

    /** 由试卷题目转换而来，逐个字段显式拷贝，确保不会顺手把答案带出去 */
    public static List<ExamQuestionView> from(List<ExamPaperQuestion> paperQuestions) {
        List<ExamQuestionView> views = new ArrayList<ExamQuestionView>();
        if (paperQuestions == null) {
            return views;
        }
        for (ExamPaperQuestion pq : paperQuestions) {
            ExamQuestionView view = new ExamQuestionView();
            view.sortOrder = pq.getSortOrder() == null ? views.size() + 1 : pq.getSortOrder();
            view.questionId = pq.getQuestionId();
            view.type = pq.getType();
            view.typeText = pq.getTypeText();
            view.knowledgePoint = pq.getKnowledgePoint();
            view.content = pq.getContent();
            view.optionA = pq.getOptionA();
            view.optionB = pq.getOptionB();
            view.optionC = pq.getOptionC();
            view.optionD = pq.getOptionD();
            view.score = pq.getScore();
            views.add(view);
        }
        return views;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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

    public String getTypeText() {
        return typeText;
    }

    public void setTypeText(String typeText) {
        this.typeText = typeText;
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

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }
}
