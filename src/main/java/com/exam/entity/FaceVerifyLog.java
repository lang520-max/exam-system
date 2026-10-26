package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人脸核验留痕。
 *
 * <p>这张表是**审计记录**与**开考门禁依据**两用的：
 * {@code ExamService.startExam()} 不去查内存状态，而是查本表里近期是否存在
 * 该考生该场考试的 PASS / OVERRIDE_PASS —— 服务重启、多实例部署都不会失效，
 * 而且「谁在什么时候因为什么被放行」天然留痕。</p>
 */
@Data
@TableName("face_verify_log")
public class FaceVerifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 考试ID（录入场景为 null） */
    private Long examId;

    /** 考试记录ID（核验时通常还没有记录，教师放行后才可能有） */
    private Long recordId;

    /** ENROLL-录入 / EXAM_START-开考前核验 / OVERRIDE-教师放行 */
    private String scene;

    /** PASS / FAIL / LIVENESS_FAIL / UNAVAILABLE / OVERRIDE_PASS */
    private String result;

    /** 厂商返回的相似度 0-100 */
    private BigDecimal confidence;

    /** 1-活体通过 / 0-未通过 / null-未检 */
    private Integer liveness;

    /** 本次开考的第几次尝试（从 1 开始） */
    private Integer retryNo;

    private String ipAddress;

    /** 失败原因 / 厂商错误码，用于排查 */
    private String detail;

    /** 人工放行时的操作人 */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}