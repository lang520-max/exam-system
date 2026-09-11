package com.caikailang.examsystem.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 整卷判分结果。
 *
 * <p>由 {@code GradeCalculator#grade} 纯函数产出，不碰数据库、不依赖 Spring 上下文，
 * 因此判分规则可以被单元测试逐条钉死（单选/多选/判断、多选少选不得分等）。</p>
 *
 * @author lang520-max
 */
public class GradeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 逐题判分明细 */
    private List<GradeItem> items = new ArrayList<GradeItem>();
    /** 学生总分 = 各题得分之和 */
    private BigDecimal totalScore = BigDecimal.ZERO;
    /** 试卷满分 = 各题分值之和 */
    private BigDecimal fullScore = BigDecimal.ZERO;
    /** 答对题数 */
    private int correctCount;
    /** 总题数 */
    private int totalCount;

    public void addItem(GradeItem item) {
        items.add(item);
        fullScore = fullScore.add(item.getFullScore() == null ? BigDecimal.ZERO : item.getFullScore());
        totalScore = totalScore.add(item.getEarnedScore() == null ? BigDecimal.ZERO : item.getEarnedScore());
        totalCount++;
        if (item.isCorrect()) {
            correctCount++;
        }
    }

    /**
     * 判定是否及格（及格线由试卷给出）。
     *
     * <p>用 {@link BigDecimal#compareTo} 而不是 {@code equals}：
     * {@code new BigDecimal("60.0").equals(new BigDecimal("60"))} 为 false，
     * 直接用 equals 会让「60 分及格、考了 60.0」被判成不及格。</p>
     */
    public boolean isPassed(BigDecimal passScore) {
        if (passScore == null) {
            return false;
        }
        return totalScore.compareTo(passScore) >= 0;
    }

    public List<GradeItem> getItems() {
        return items;
    }

    public void setItems(List<GradeItem> items) {
        this.items = items == null ? new ArrayList<GradeItem>() : items;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public BigDecimal getFullScore() {
        return fullScore;
    }

    public void setFullScore(BigDecimal fullScore) {
        this.fullScore = fullScore;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
