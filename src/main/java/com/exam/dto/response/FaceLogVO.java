package com.exam.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人脸核验流水（教师端查看用）。
 *
 * <p>字段与 {@code face_verify_log} 一一对应，另带考生姓名/账号 ——
 * 教师看到的是「谁、哪场、什么时候、结果如何、为什么」，而不是一串 user_id。</p>
 */
@Data
public class FaceLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String realName;
    private Long examId;
    private Long recordId;

    /** ENROLL / EXAM_START / OVERRIDE */
    private String scene;

    /** PASS / FAIL / LIVENESS_FAIL / UNAVAILABLE / OVERRIDE_PASS */
    private String result;

    private BigDecimal confidence;
    private Integer liveness;
    private Integer retryNo;
    private String ipAddress;
    private String detail;
    private Long operatorId;
    private LocalDateTime createdAt;
}