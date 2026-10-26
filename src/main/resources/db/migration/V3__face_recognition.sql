-- ============================================================================
--  V3 —— 人脸识别：注册采集底片 + 开考前核验 + 全过程留痕
--
--  为什么现在才做：本项目原先没有任何人脸/摄像头能力。按需求新增两条门禁：
--    1) 注册时必须完成人脸采集（活体 + 存底片）
--    2) 开考前必须人脸核验通过，否则不允许开考
--  身份比对走阿里云「视觉智能开放平台 - 人脸比对 1:1（CompareFace）」，
--  因此库里只存【图片】不存特征向量（比对本就在厂商侧完成）。
--
--  设计取舍（都写在这里，避免以后有人猜）：
--    · user_face 一人一条（UNIQUE user_id）：底片以 JPEG 小图（前端压到 640×480、
--      质量 0.7）存 MEDIUMTEXT，约 20-40KB；随库备份，不引入对象存储。
--    · face_verify_log 是「审计 + 放行依据」两用：开考门禁不去查内存状态，
--      而是查这张表里近期是否有 PASS/OVERRIDE_PASS —— 服务重启/多实例都不会失效。
--    · result 用字符串而不是数字：排障时直接看得懂，且以后加结果值不用改语义。
--    · 绝不因为「人脸服务不可用」就静默放行：写入 UNAVAILABLE 并由教师人工放行
--      （降级放行等于门禁失效，比拦错人更糟）。
-- ============================================================================

-- ---------------------------------------------------------------- 1. 人脸底片
CREATE TABLE `user_face` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL                COMMENT '用户ID（一人一条）',
    `image_base64`  MEDIUMTEXT   NOT NULL                COMMENT '注册底片：data:image/jpeg;base64,... 或纯 base64',
    `image_sha256`  CHAR(64)     DEFAULT NULL            COMMENT '底片摘要：判断底片是否被换过',
    `quality_score` DECIMAL(5,2) DEFAULT NULL             COMMENT '厂商返回的人脸质量分（可选）',
    `source`        VARCHAR(24)  DEFAULT 'ENROLL'        COMMENT '来源：ENROLL-注册录入 / SELF-自助补录',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_face_user` (`user_id`),
    CONSTRAINT `fk_face_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户人脸底片';

-- ---------------------------------------------------------------- 2. 核验留痕
CREATE TABLE `face_verify_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL                COMMENT '考生ID',
    `exam_id`     BIGINT       DEFAULT NULL            COMMENT '考试ID（录入场景为空）',
    `record_id`   BIGINT       DEFAULT NULL            COMMENT '考试记录ID（放行后才可能有）',
    `scene`       VARCHAR(24)  NOT NULL                COMMENT 'ENROLL-录入 / EXAM_START-开考前核验 / OVERRIDE-教师放行',
    `result`      VARCHAR(16)  NOT NULL                COMMENT 'PASS / FAIL / LIVENESS_FAIL / UNAVAILABLE / OVERRIDE_PASS',
    `confidence`  DECIMAL(5,2) DEFAULT NULL             COMMENT '厂商返回的相似度 0-100',
    `liveness`    TINYINT      DEFAULT NULL            COMMENT '1-活体通过 / 0-未通过 / NULL-未检',
    `retry_no`    INT          DEFAULT 0               COMMENT '本次开考的第几次尝试（从 1 开始）',
    `ip_address`  VARCHAR(50)  DEFAULT NULL            COMMENT '请求来源 IP（forward-headers 已开启，取真实客户端）',
    `detail`      VARCHAR(255) DEFAULT NULL            COMMENT '失败原因 / 厂商错误码，用于排查',
    `operator_id` BIGINT       DEFAULT NULL            COMMENT '人工放行时的操作人ID',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_face_log_user_exam` (`user_id`, `exam_id`, `created_at`),
    KEY `idx_face_log_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸核验留痕（审计 + 放行依据）';

-- ---------------------------------------------------------------- 3. 答卷上的人脸状态
-- 放在 exam_record 上而不是只靠 log：成绩单/教师端展示时不必再聚合日志表。
ALTER TABLE `exam_record`
    ADD COLUMN `face_verified`     TINYINT      DEFAULT 0    COMMENT '是否已通过人脸核验（含人工放行）：0-否 / 1-是'
        AFTER `ip_address`,
    ADD COLUMN `face_confidence`   DECIMAL(5,2) DEFAULT NULL COMMENT '通过时的相似度（人工放行为 NULL）'
        AFTER `face_verified`,
    ADD COLUMN `face_verified_at`  DATETIME     DEFAULT NULL COMMENT '核验通过时间'
        AFTER `face_confidence`;