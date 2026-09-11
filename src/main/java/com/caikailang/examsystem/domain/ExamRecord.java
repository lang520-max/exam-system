package com.caikailang.examsystem.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 考试记录：一个学生参加一场考试对应的唯一一条记录。
 *
 * <p>生命周期：{@code ONGOING}（已开考，正在答题）→ {@code SUBMITTED}（已交卷，分数已算）。</p>
 *
 * <p>防重复交卷靠两层保障：</p>
 * <ol>
 *   <li>数据库唯一约束 {@code UNIQUE(paper_id, student_id)}：同一场考试一个学生只能有一条记录；</li>
 *   <li>交卷时用条件更新 {@code UPDATE ... WHERE id = ? AND status = 'ONGOING'}，
 *       受影响行数为 0 即说明已被并发请求抢先交卷，直接抛业务异常，绝不重复计分。</li>
 * </ol>
 *
 * @author lang520-max
 */
public class ExamRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    private Long id;
    private Long paperId;
    private Long studentId;
    /** 开考时间，与试卷时长共同决定交卷截止时刻 */
    private Date startTime;
    private Date submitTime;
    /** 本次得分（判分后落库） */
    private BigDecimal score;
    /** 答对题数 */
    private Integer correctCount;
    /** 试卷总题数 */
    private Integer totalCount;
    /** ONGOING / SUBMITTED */
    private String status;
    /** 是否超时交卷 */
    private Boolean timeout;
    private Date createdAt;
    private Date updatedAt;

    /* ---------- 以下为关联查询带出的展示字段，不落库 ---------- */

    private String paperTitle;
    private String paperSubject;
    /** 试卷总分（快照，来自 exam_paper.total_score） */
    private BigDecimal paperTotalScore;
    /** 及格分（快照，来自 exam_paper.pass_score） */
    private BigDecimal passScore;
    private Integer durationMinutes;
    private String studentNickname;
    private String studentUsername;

    public boolean isOngoing() {
        return STATUS_ONGOING.equals(status);
    }

    public boolean isSubmitted() {
        return STATUS_SUBMITTED.equals(status);
    }

    /** 是否及格：已交卷且得分不低于及格分 */
    public boolean isPassed() {
        return isSubmitted() && score != null && passScore != null && score.compareTo(passScore) >= 0;
    }

    /** 交卷截止时刻 = 开考时间 + 考试时长 */
    public Date getDeadline() {
        if (startTime == null || durationMinutes == null) {
            return null;
        }
        return new Date(startTime.getTime() + durationMinutes.longValue() * 60L * 1000L);
    }

    /** 截止时刻的毫秒时间戳，供前端倒计时直接用（避免浏览器时区误差） */
    public Long getDeadlineMillis() {
        Date deadline = getDeadline();
        return deadline == null ? null : Long.valueOf(deadline.getTime());
    }

    /** 得分率百分比，用于成绩列表展示（0~100 的整数） */
    public int getScorePercent() {
        if (score == null || paperTotalScore == null || paperTotalScore.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return score.multiply(BigDecimal.valueOf(100))
            .divide(paperTotalScore, 0, java.math.RoundingMode.HALF_UP)
            .intValue();
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

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime) {
        this.submitTime = submitTime;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(Integer correctCount) {
        this.correctCount = correctCount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getTimeout() {
        return timeout;
    }

    public void setTimeout(Boolean timeout) {
        this.timeout = timeout;
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

    public String getPaperTitle() {
        return paperTitle;
    }

    public void setPaperTitle(String paperTitle) {
        this.paperTitle = paperTitle;
    }

    public String getPaperSubject() {
        return paperSubject;
    }

    public void setPaperSubject(String paperSubject) {
        this.paperSubject = paperSubject;
    }

    public BigDecimal getPaperTotalScore() {
        return paperTotalScore;
    }

    public void setPaperTotalScore(BigDecimal paperTotalScore) {
        this.paperTotalScore = paperTotalScore;
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

    public String getStudentNickname() {
        return studentNickname;
    }

    public void setStudentNickname(String studentNickname) {
        this.studentNickname = studentNickname;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public void setStudentUsername(String studentUsername) {
        this.studentUsername = studentUsername;
    }
}
