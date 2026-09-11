-- ============================================================================
--  在线考试 / 题库系统 —— MySQL 8 建表脚本
--  执行顺序：先本脚本建库建表，再执行 data.sql 灌入演示数据
--  命令行：mysql -uroot -p < sql/schema.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `exam_system`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `exam_system`;

SET NAMES utf8mb4;

-- 按依赖关系倒序删除，避免外键约束报错
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `exam_answer`;
DROP TABLE IF EXISTS `exam_record`;
DROP TABLE IF EXISTS `exam_paper_question`;
DROP TABLE IF EXISTS `exam_paper`;
DROP TABLE IF EXISTS `question`;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- 1. 用户表：教师（TEACHER，即管理员）与学生（STUDENT）共用
-- ----------------------------------------------------------------------------
CREATE TABLE `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`   VARCHAR(20)  NOT NULL                COMMENT '登录名',
  `password`   VARCHAR(100) NOT NULL                COMMENT 'BCrypt 哈希（绝不存明文）',
  `nickname`   VARCHAR(20)  NOT NULL                COMMENT '昵称',
  `role`       VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT 'TEACHER=教师/管理员，STUDENT=学生',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 登录名唯一：并发注册同名账号时由数据库兜底（Service 里已先查一次给出友好提示）
  UNIQUE KEY `uk_user_username` (`username`),
  -- 按角色筛选用户（教师列表 / 学生列表）
  KEY `idx_user_role` (`role`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ----------------------------------------------------------------------------
-- 2. 题目表（题库）
-- ----------------------------------------------------------------------------
CREATE TABLE `question` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type`            VARCHAR(16)   NOT NULL                COMMENT 'SINGLE=单选，MULTIPLE=多选，JUDGE=判断',
  `subject`         VARCHAR(32)   NOT NULL                COMMENT '科目，随机组卷的抽题维度',
  `knowledge_point` VARCHAR(64)   DEFAULT NULL            COMMENT '知识点/章节',
  `content`         VARCHAR(500)  NOT NULL                COMMENT '题干',
  `option_a`        VARCHAR(200)  DEFAULT NULL            COMMENT '选项 A（判断题为空）',
  `option_b`        VARCHAR(200)  DEFAULT NULL            COMMENT '选项 B（判断题为空）',
  `option_c`        VARCHAR(200)  DEFAULT NULL            COMMENT '选项 C（判断题为空）',
  `option_d`        VARCHAR(200)  DEFAULT NULL            COMMENT '选项 D（判断题为空）',
  `answer`          VARCHAR(16)   NOT NULL                COMMENT '正确答案：单选/判断形如 A，多选形如 ABD',
  `score`           DECIMAL(6, 1) NOT NULL DEFAULT 5.0    COMMENT '题库默认分值',
  `difficulty`      VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM' COMMENT 'EASY/MEDIUM/HARD',
  `creator_id`      BIGINT        NOT NULL                COMMENT '出题教师',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 随机组卷的核心索引：按「科目 + 题型」筛选后 ORDER BY RAND() LIMIT n
  KEY `idx_question_subject_type` (`subject`, `type`),
  -- 教师查看「我出的题」
  KEY `idx_question_creator` (`creator_id`),
  -- 按难度筛选
  KEY `idx_question_difficulty` (`difficulty`),
  CONSTRAINT `fk_question_creator` FOREIGN KEY (`creator_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题目表';

-- ----------------------------------------------------------------------------
-- 3. 试卷表
-- ----------------------------------------------------------------------------
CREATE TABLE `exam_paper` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`            VARCHAR(100)  NOT NULL                COMMENT '试卷标题',
  `subject`          VARCHAR(32)   NOT NULL                COMMENT '科目',
  `total_score`      DECIMAL(6, 1) NOT NULL                COMMENT '总分（组卷时按题目分值累加落库）',
  `pass_score`       DECIMAL(6, 1) NOT NULL                COMMENT '及格分',
  `duration_minutes` INT           NOT NULL                COMMENT '考试时长（分钟）',
  `status`           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT=草稿，PUBLISHED=已发布，CLOSED=已结束',
  `creator_id`       BIGINT        NOT NULL                COMMENT '组卷教师',
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 学生端只查 PUBLISHED 的试卷，教师端按状态筛选
  KEY `idx_paper_status` (`status`),
  KEY `idx_paper_creator` (`creator_id`),
  CONSTRAINT `fk_paper_creator` FOREIGN KEY (`creator_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '试卷表';

-- ----------------------------------------------------------------------------
-- 4. 试卷-题目关联表（含每题分值快照）
-- ----------------------------------------------------------------------------
CREATE TABLE `exam_paper_question` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `paper_id`    BIGINT        NOT NULL                COMMENT '试卷 ID',
  `question_id` BIGINT        NOT NULL                COMMENT '题目 ID',
  `score`       DECIMAL(6, 1) NOT NULL                COMMENT '该题在本卷中的分值快照',
  `sort_order`  INT           NOT NULL DEFAULT 0      COMMENT '题号，从 1 开始',
  PRIMARY KEY (`id`),
  -- 同一道题不能在一份试卷里出现两次
  UNIQUE KEY `uk_paper_question` (`paper_id`, `question_id`),
  -- 取整卷题目时按 paper_id 查，并按 sort_order 排序
  KEY `idx_pq_paper_sort` (`paper_id`, `sort_order`),
  -- 删除题目 / 统计引用次数时按 question_id 反查
  KEY `idx_pq_question` (`question_id`),
  CONSTRAINT `fk_pq_paper` FOREIGN KEY (`paper_id`) REFERENCES `exam_paper` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pq_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '试卷题目关联表';

-- ----------------------------------------------------------------------------
-- 5. 考试记录表
-- ----------------------------------------------------------------------------
CREATE TABLE `exam_record` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `paper_id`      BIGINT        NOT NULL                COMMENT '试卷 ID',
  `student_id`    BIGINT        NOT NULL                COMMENT '考生 ID',
  `start_time`    DATETIME      NOT NULL                COMMENT '开考时间',
  `submit_time`   DATETIME      DEFAULT NULL            COMMENT '交卷时间，未交卷为 NULL',
  `score`         DECIMAL(6, 1) NOT NULL DEFAULT 0.0    COMMENT '得分',
  `correct_count` INT           NOT NULL DEFAULT 0      COMMENT '答对题数',
  `total_count`   INT           NOT NULL DEFAULT 0      COMMENT '试卷总题数',
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'ONGOING' COMMENT 'ONGOING=答题中，SUBMITTED=已交卷',
  `is_timeout`    TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否超时交卷',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 防重复开考的核心约束：一场考试一个学生只能有一条记录
  UNIQUE KEY `uk_record_paper_student` (`paper_id`, `student_id`),
  -- 学生查「我的成绩」：按学生 + 状态过滤
  KEY `idx_record_student_status` (`student_id`, `status`),
  -- 教师查某场考试成绩与统计
  KEY `idx_record_paper_status` (`paper_id`, `status`),
  CONSTRAINT `fk_record_paper` FOREIGN KEY (`paper_id`) REFERENCES `exam_paper` (`id`),
  CONSTRAINT `fk_record_student` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试记录表';

-- ----------------------------------------------------------------------------
-- 6. 答题明细表（判分结果快照）
-- ----------------------------------------------------------------------------
CREATE TABLE `exam_answer` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_id`      BIGINT        NOT NULL                COMMENT '考试记录 ID',
  `question_id`    BIGINT        NOT NULL                COMMENT '题目 ID',
  `student_answer` VARCHAR(16)   DEFAULT NULL            COMMENT '学生作答，未作答为 NULL',
  `correct_answer` VARCHAR(16)   NOT NULL                COMMENT '判分时的正确答案快照',
  `score`          DECIMAL(6, 1) NOT NULL                COMMENT '该题满分快照',
  `earned_score`   DECIMAL(6, 1) NOT NULL                COMMENT '实际得分',
  `is_correct`     TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否答对',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  -- 防重复交卷的第二道保险：同一记录同一题只能有一条作答
  UNIQUE KEY `uk_answer_record_question` (`record_id`, `question_id`),
  -- 成绩单与错题回顾：按 record_id 取明细，错题再看 is_correct
  KEY `idx_answer_record_correct` (`record_id`, `is_correct`),
  KEY `idx_answer_question` (`question_id`),
  CONSTRAINT `fk_answer_record` FOREIGN KEY (`record_id`) REFERENCES `exam_record` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '答题明细表';
