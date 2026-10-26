-- ============================================================================
--  V1 —— 初始表结构
--
--  6 张核心表，按外键依赖顺序创建：
--    sys_user → question_bank → question → exam → exam_record → student_answer
--
--  与 sql/init.sql 的建表结果一致（sql/init.sql 保留作为手工初始化脚本）。
--
--  与 sql/init.sql 的两点刻意差异：
--    1. 不含 CREATE DATABASE / USE —— Flyway 连接的已经是目标库；
--       库本身由 docker-compose 的 MYSQL_DATABASE 或云数据库控制台创建。
--    2. 不含 DROP TABLE —— 迁移脚本一旦执行过就不会重跑，
--       带上 DROP 只会在「老库 + baseline-on-migrate」场景下造成误删风险。
--
--  迁移脚本不可修改：Flyway 会校验 checksum。
--  表结构变更请新增 V2__xxx.sql、V3__xxx.sql。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 系统用户表
-- ----------------------------------------------------------------------------
CREATE TABLE `sys_user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(50)  NOT NULL                COMMENT '登录名',
    `password`   VARCHAR(255) NOT NULL                COMMENT 'BCrypt 哈希',
    `real_name`  VARCHAR(50)  DEFAULT NULL            COMMENT '真实姓名',
    `email`      VARCHAR(100) DEFAULT NULL            COMMENT '邮箱',
    `phone`      VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    `role`       TINYINT      NOT NULL DEFAULT 2      COMMENT '0-管理员 1-教师 2-学生',
    `avatar`     VARCHAR(500) DEFAULT NULL            COMMENT '头像URL',
    `status`     TINYINT      DEFAULT 1               COMMENT '0-禁用 1-启用',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ----------------------------------------------------------------------------
-- 2. 题库表
-- ----------------------------------------------------------------------------
CREATE TABLE `question_bank` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL                COMMENT '题库名称',
    `subject`     VARCHAR(50)  DEFAULT NULL            COMMENT '科目',
    `description` TEXT         DEFAULT NULL            COMMENT '描述',
    `created_by`  BIGINT       NOT NULL                COMMENT '创建人',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_subject` (`subject`),
    KEY `idx_created_by` (`created_by`),
    CONSTRAINT `fk_bank_creator` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库表';

-- ----------------------------------------------------------------------------
-- 3. 题目表
-- ----------------------------------------------------------------------------
CREATE TABLE `question` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `bank_id`     BIGINT       NOT NULL                COMMENT '所属题库',
    `type`        TINYINT      NOT NULL                COMMENT '0-单选 1-多选 2-判断 3-填空 4-简答',
    `difficulty`  TINYINT      NOT NULL DEFAULT 2      COMMENT '1-简单 2-中等 3-困难',
    `content`     TEXT         NOT NULL                COMMENT '题目内容',
    `options`     JSON         DEFAULT NULL            COMMENT '选项JSON [{label:"A",content:"xxx"},...]',
    `answer`      TEXT         NOT NULL                COMMENT '标准答案',
    `score`       INT          NOT NULL DEFAULT 5      COMMENT '分值',
    `analysis`    TEXT         DEFAULT NULL            COMMENT '题目解析',
    `status`      TINYINT      DEFAULT 1               COMMENT '0-禁用 1-启用',
    `created_by`  BIGINT       NOT NULL                COMMENT '出题人',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_bank_type` (`bank_id`, `type`),
    KEY `idx_type_difficulty` (`type`, `difficulty`),
    KEY `idx_created_by` (`created_by`),
    CONSTRAINT `fk_question_bank` FOREIGN KEY (`bank_id`) REFERENCES `question_bank` (`id`),
    CONSTRAINT `fk_question_creator` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';

-- ----------------------------------------------------------------------------
-- 4. 考试表
-- ----------------------------------------------------------------------------
CREATE TABLE `exam` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(200) NOT NULL                COMMENT '考试名称',
    `description` TEXT         DEFAULT NULL            COMMENT '描述',
    `bank_id`     BIGINT       NOT NULL                COMMENT '所属题库',
    `duration`    INT          NOT NULL                COMMENT '考试时长(分钟)',
    `total_score` INT          NOT NULL DEFAULT 100    COMMENT '总分',
    `pass_score`  INT          DEFAULT 60              COMMENT '及格分',
    `rule_config` JSON         DEFAULT NULL            COMMENT '组卷规则JSON',
    `question_snapshot` JSON   DEFAULT NULL            COMMENT '试卷题目快照 [{questionId,score},...]',
    `start_time`  DATETIME     DEFAULT NULL            COMMENT '考试开始时间',
    `end_time`    DATETIME     DEFAULT NULL            COMMENT '考试结束时间',
    `status`      TINYINT      DEFAULT 0               COMMENT '0-未发布 1-进行中 2-已结束',
    `created_by`  BIGINT       NOT NULL                COMMENT '创建人',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_bank_id` (`bank_id`),
    KEY `idx_created_by` (`created_by`),
    CONSTRAINT `fk_exam_bank` FOREIGN KEY (`bank_id`) REFERENCES `question_bank` (`id`),
    CONSTRAINT `fk_exam_creator` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试表';

-- ----------------------------------------------------------------------------
-- 5. 考试记录表
-- ----------------------------------------------------------------------------
CREATE TABLE `exam_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `exam_id`     BIGINT       NOT NULL                COMMENT '考试ID',
    `user_id`     BIGINT       NOT NULL                COMMENT '考生ID',
    `start_time`  DATETIME     NOT NULL                COMMENT '实际开始时间',
    `end_time`    DATETIME     DEFAULT NULL            COMMENT '实际交卷时间',
    `score`       INT          DEFAULT NULL            COMMENT '最终得分',
    `status`      TINYINT      DEFAULT 0               COMMENT '0-考试中 1-待判分 2-已出分 3-超时',
    `ip_address`  VARCHAR(50)  DEFAULT NULL            COMMENT 'IP地址',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_user` (`exam_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_record_exam` FOREIGN KEY (`exam_id`) REFERENCES `exam` (`id`),
    CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试记录表';

-- ----------------------------------------------------------------------------
-- 6. 学生答案表
-- ----------------------------------------------------------------------------
CREATE TABLE `student_answer` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `record_id`   BIGINT       NOT NULL                COMMENT '考试记录ID',
    `question_id` BIGINT       NOT NULL                COMMENT '题目ID',
    `answer`      TEXT         DEFAULT NULL            COMMENT '学生答案',
    `score`       INT          DEFAULT 0               COMMENT '本题得分',
    `is_correct`  TINYINT      DEFAULT 0               COMMENT '0-错误 1-正确 2-部分正确',
    `reviewed_by` BIGINT       DEFAULT NULL            COMMENT '人工复核人',
    `reviewed_at` DATETIME     DEFAULT NULL            COMMENT '复核时间',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_question` (`record_id`, `question_id`),
    KEY `idx_record_id` (`record_id`),
    KEY `idx_question_id` (`question_id`),
    CONSTRAINT `fk_answer_record` FOREIGN KEY (`record_id`) REFERENCES `exam_record` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生答案表';