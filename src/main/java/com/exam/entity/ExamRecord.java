package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试记录
 */
@Data
@TableName("exam_record")
public class ExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long userId;

    /** 实际开始时间 */
    private LocalDateTime startTime;

    /** 实际交卷时间 */
    private LocalDateTime endTime;

    /** 最终得分 */
    private Integer score;

    /** 0-考试中 1-待判分 2-已出分 3-超时 */
    private Integer status;

    private String ipAddress;

    /**
     * 是否已通过人脸核验（含教师人工放行）：0-否 / 1-是。
     *
     * <p>开考前必须为 1 才能开考。门禁的真正判定依据是 face_verify_log 里近期的
     * PASS/OVERRIDE_PASS 记录（查库而不是查内存，重启/多实例都不会失效）；
     * 这个字段只是把结果固化到答卷上，方便成绩单与教师端直接展示。</p>
     */
    private Integer faceVerified;

    /** 核验通过时的相似度（教师人工放行为 null） */
    private java.math.BigDecimal faceConfidence;

    /** 核验通过时间 */
    private LocalDateTime faceVerifiedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}