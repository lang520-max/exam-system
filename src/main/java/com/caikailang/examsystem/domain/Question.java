package com.caikailang.examsystem.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 题目（题库中的一条记录）。
 *
 * <p>题型与答案约定：</p>
 * <ul>
 *   <li>{@code SINGLE} 单选：答案形如 {@code "A"}</li>
 *   <li>{@code MULTIPLE} 多选：答案形如 {@code "ABD"}（按字母升序存储，判分与顺序无关）</li>
 *   <li>{@code JUDGE} 判断：答案统一为 {@code "A"}（正确）/ {@code "B"}（错误），选项列留空</li>
 * </ul>
 *
 * <p>分值用 {@link BigDecimal} 而非 {@code double}：考试分数是金额级精度的业务数据，
 * 浮点数累加会出现 {@code 0.1 + 0.2 = 0.30000000000000004} 这类误差，
 * 会让「满分 100」和「及格线 60」的比较出现意外结果。</p>
 *
 * @author 蔡开浪
 */
public class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_MULTIPLE = "MULTIPLE";
    public static final String TYPE_JUDGE = "JUDGE";

    public static final String DIFFICULTY_EASY = "EASY";
    public static final String DIFFICULTY_MEDIUM = "MEDIUM";
    public static final String DIFFICULTY_HARD = "HARD";

    private Long id;
    /** SINGLE / MULTIPLE / JUDGE */
    private String type;
    /** 科目，随机组卷的抽题维度之一 */
    private String subject;
    /** 知识点，便于按章节检索 */
    private String knowledgePoint;
    private String content;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    /** 正确答案，见类注释的编码约定 */
    private String answer;
    /** 题库中的默认分值；入卷时会被试卷上的分值快照覆盖 */
    private BigDecimal score;
    /** EASY / MEDIUM / HARD */
    private String difficulty;
    private Long creatorId;
    private Date createdAt;
    private Date updatedAt;

    /* ---------- 以下为关联查询带出的展示字段，不落库 ---------- */

    private String creatorNickname;

    public boolean isMultiple() {
        return TYPE_MULTIPLE.equals(type);
    }

    public boolean isJudge() {
        return TYPE_JUDGE.equals(type);
    }

    /** 题型中文名 */
    public String getTypeText() {
        if (TYPE_SINGLE.equals(type)) {
            return "单选题";
        }
        if (TYPE_MULTIPLE.equals(type)) {
            return "多选题";
        }
        if (TYPE_JUDGE.equals(type)) {
            return "判断题";
        }
        return "未知题型";
    }

    /** 难度中文名 */
    public String getDifficultyText() {
        if (DIFFICULTY_EASY.equals(difficulty)) {
            return "简单";
        }
        if (DIFFICULTY_HARD.equals(difficulty)) {
            return "困难";
        }
        return "中等";
    }

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

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatorNickname() {
        return creatorNickname;
    }

    public void setCreatorNickname(String creatorNickname) {
        this.creatorNickname = creatorNickname;
    }
}
