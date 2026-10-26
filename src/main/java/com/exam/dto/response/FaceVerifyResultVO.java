package com.exam.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 一次人脸核验的结果。
 *
 * <p>{@code result} 的取值与 {@code face_verify_log.result} 一致：
 * PASS / FAIL / LIVENESS_FAIL / UNAVAILABLE，另有 BLOCKED（已达重试上限）与
 * NEED_ENROLL（还没录底片）这两种「流程性」结果。</p>
 */
@Data
public class FaceVerifyResultVO {

    private String result;

    /** 是否放行（只有 PASS 为 true —— UNAVAILABLE 必须由教师放行，不自动通融） */
    private boolean passed;

    private BigDecimal confidence;

    private double threshold;

    /** 还能试几次 */
    private int retriesLeft;

    /** 是否已被禁考 */
    private boolean blocked;

    /** 给学生的提示语（失败原因已脱敏，不暴露厂商内部错误） */
    private String message;

    /** 需要人工放行时，提示学生去找监考老师 */
    private boolean needTeacherOverride;
}