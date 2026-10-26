package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户人脸底片（一人一条）。
 *
 * <p>存的是**图片**而不是特征向量：本项目的人脸比对走阿里云
 * 「视觉智能开放平台 - 人脸比对 1:1（CompareFace）」，比对在厂商侧完成，
 * 本地不需要（也没有能力）跑人脸模型。图片用前端压好的 JPEG 小图
 * （最长边 640、质量 0.7），base64 后约 20-40KB，随库备份、不引对象存储。</p>
 */
@Data
@TableName("user_face")
public class UserFace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** data:image/jpeg;base64,... 或纯 base64 */
    private String imageBase64;

    /** 底片摘要：判断底片是否被换过（重新录入时会变） */
    private String imageSha256;

    /** 厂商返回的人脸质量分（可选，识别不到时为 null） */
    private BigDecimal qualityScore;

    /** ENROLL-注册时录入 / SELF-登录后自助补录 */
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}