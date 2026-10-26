package com.exam.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人脸状态（学生端在开考前/录入页展示；教师端也能看）。
 */
@Data
public class FaceStatusVO {

    /** 人脸识别功能是否启用（关掉时前端不该拦人） */
    private boolean enabled;

    /** 是否已录入底片 */
    private boolean enrolled;

    private LocalDateTime enrolledAt;

    /** 当前是否处于「核验通过」的有效窗口内 */
    private boolean passed;

    /** 通过时的相似度 */
    private BigDecimal confidence;

    private LocalDateTime verifiedAt;

    /** 已失败次数（只算 FAIL / LIVENESS_FAIL，不算服务不可用） */
    private int failedAttempts;

    /** 允许的重试上限 */
    private int maxRetries;

    /** 是否已达上限被禁考（教师放行可解） */
    private boolean blocked;

    /** 最近一次结果：PASS/FAIL/LIVENESS_FAIL/UNAVAILABLE/OVERRIDE_PASS，或空 */
    private String lastResult;

    /** 最近一次失败原因（给学生的提示） */
    private String lastDetail;

    /** 通过后的有效窗口（秒）—— 前端可提示「核验结果 5 分钟内有效」 */
    private int passWindowSeconds;

    /** 相似度阈值（0-100），前端用它解释「差多少」 */
    private double threshold;
}