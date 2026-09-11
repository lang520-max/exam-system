package com.caikailang.examsystem.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 试卷。
 *
 * <p>状态流转：{@code DRAFT}（草稿，仅教师可见）→ {@code PUBLISHED}（已发布，学生可参加）
 * → {@code CLOSED}（已结束，不再接受新的开考，但历史成绩保留）。</p>
 *
 * <p>{@code totalScore} 在组卷时按各题分值累加后落库（不每次实时算），
 * {@code passScore} 用于及格判定：{@code 得分 >= passScore} 即为及格。</p>
 *
 * @author lang520-max
 */
public class ExamPaper implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_CLOSED = "CLOSED";

    private Long id;
    private String title;
    private String subject;
    /** 试卷总分，组卷时按题目分值累加 */
    private BigDecimal totalScore;
    /** 及格分 */
    private BigDecimal passScore;
    /** 考试时长（分钟），与考试开始时间共同决定交卷截止时刻 */
    private Integer durationMinutes;
    /** DRAFT / PUBLISHED / CLOSED */
    private String status;
    private Long creatorId;
    private Date createdAt;
    private Date updatedAt;

    /* ---------- 以下为关联查询带出的展示字段，不落库 ---------- */

    private String creatorNickname;
    /** 试卷包含的题目数量 */
    private Integer questionCount;
    /** 学生端列表专用：当前学生在这份试卷上的考试记录 ID（未开考为 null） */
    private Long recordId;
    /** 学生端列表专用：考试记录状态 ONGOING / SUBMITTED */
    private String recordStatus;
    /** 学生端列表专用：已交卷时的得分 */
    private BigDecimal recordScore;

    public String getStatusText() {
        if (STATUS_PUBLISHED.equals(status)) {
            return "已发布";
        }
        if (STATUS_CLOSED.equals(status)) {
            return "已结束";
        }
        return "草稿";
    }

    public boolean isPublished() {
        return STATUS_PUBLISHED.equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public BigDecimal getPassScore() {
        return passScore;
    }

    public void setPassScore(BigDecimal passScore) {
        this.passScore = passScore;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public BigDecimal getRecordScore() {
        return recordScore;
    }

    public void setRecordScore(BigDecimal recordScore) {
        this.recordScore = recordScore;
    }
}
