package com.caikailang.examsystem.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一场考试的成绩统计（教师端「成绩统计」使用）。
 *
 * <p>聚合部分（人数、平均/最高/最低分、及格人数）由一条 SQL 在数据库侧完成，
 * 及格率这类派生指标在 Java 里按 {@code passCount / totalCount} 计算并保留一位小数，
 * 避免把业务口径散落在 SQL 里。</p>
 *
 * @author lang520-max
 */
public class ExamStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long paperId;
    private String paperTitle;
    /** 已交卷人数 */
    private long totalCount;
    /** 及格人数（得分 >= 及格分） */
    private long passCount;
    private BigDecimal avgScore = BigDecimal.ZERO;
    private BigDecimal maxScore = BigDecimal.ZERO;
    private BigDecimal minScore = BigDecimal.ZERO;
    /** 及格线，展示用 */
    private BigDecimal passScore = BigDecimal.ZERO;

    /** 及格率百分比，保留一位小数；无人交卷时返回 0.0 而不是抛除零异常 */
    public BigDecimal getPassRate() {
        if (totalCount <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(passCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalCount), 1, RoundingMode.HALF_UP);
    }

    public Long getPaperId() {
        return paperId;
    }

    public void setPaperId(Long paperId) {
        this.paperId = paperId;
    }

    public String getPaperTitle() {
        return paperTitle;
    }

    public void setPaperTitle(String paperTitle) {
        this.paperTitle = paperTitle;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getPassCount() {
        return passCount;
    }

    public void setPassCount(long passCount) {
        this.passCount = passCount;
    }

    public BigDecimal getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(BigDecimal avgScore) {
        this.avgScore = avgScore == null ? BigDecimal.ZERO : avgScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore == null ? BigDecimal.ZERO : maxScore;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public void setMinScore(BigDecimal minScore) {
        this.minScore = minScore == null ? BigDecimal.ZERO : minScore;
    }

    public BigDecimal getPassScore() {
        return passScore;
    }

    public void setPassScore(BigDecimal passScore) {
        this.passScore = passScore == null ? BigDecimal.ZERO : passScore;
    }
}
