package com.exam.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的考试记录」视图对象。
 *
 * <p>为什么不直接返回 {@code ExamRecord} 实体：成绩列表要显示试卷标题、总分、及格分，
 * 这些字段来自 {@code exam} 表。如果返回裸实体，前端就得再拉一次试卷列表自己做 id→title 映射，
 * 而那个接口会把草稿试卷也一并吐给非教师用户。由服务端一次性组装好，
 * 既省一次往返，也避免把不该暴露的数据发出去。</p>
 */
@Data
public class MyRecordVO {

    private Long recordId;
    private Long examId;
    private String examTitle;
    private String examSubject;

    /** 得分；未出分为 null */
    private Integer score;
    /** 试卷总分 */
    private Integer totalScore;
    /** 及格分 */
    private Integer passScore;
    /** true 及格 / false 不及格 / null 未出分 */
    private Boolean passed;

    /** 0-考试中 1-待判分 2-已出分 3-超时 */
    private Integer status;
    /** 状态中文，避免前端到处写映射 */
    private String statusText;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 考试时长（分钟） */
    private Integer durationMinutes;

    /**
     * 交卷截止时刻的 <b>epoch 毫秒</b>（= 开考时间 + 时长）。
     *
     * <p>刻意由服务端下发毫秒时间戳，而不是让浏览器去解析
     * {@code "yyyy-MM-dd HH:mm:ss"} 字符串：字符串没有时区信息，
     * 浏览器按本地时区解析会在跨时区（或服务器时区非 UTC+8）时整体偏移，
     * 倒计时就会算错。时间戳是唯一无歧义的表达。</p>
     */
    private Long deadlineMillis;
}