CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    real_name  VARCHAR(50)  DEFAULT NULL,
    email      VARCHAR(100) DEFAULT NULL,
    phone      VARCHAR(20)  DEFAULT NULL,
    role       TINYINT      NOT NULL DEFAULT 2,
    avatar     VARCHAR(500) DEFAULT NULL,
    status     TINYINT      DEFAULT 1,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS question_bank (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    subject     VARCHAR(50)  DEFAULT NULL,
    description TEXT         DEFAULT NULL,
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS question (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_id     BIGINT       NOT NULL,
    type        TINYINT      NOT NULL,
    difficulty  TINYINT      NOT NULL DEFAULT 2,
    content     TEXT         NOT NULL,
    options     VARCHAR(2000) DEFAULT NULL,
    answer      TEXT         NOT NULL,
    score       INT          NOT NULL DEFAULT 5,
    analysis    TEXT         DEFAULT NULL,
    -- 知识点/专题（V4 新增）：如「一元函数积分学」「语法」
    category    VARCHAR(50)  DEFAULT NULL,
    status      TINYINT      DEFAULT 1,
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT         DEFAULT NULL,
    bank_id     BIGINT       NOT NULL,
    duration    INT          NOT NULL,
    total_score INT          NOT NULL DEFAULT 100,
    pass_score  INT          DEFAULT 60,
    -- 试卷类型（V4 新增）：模拟卷 / 专项训练
    paper_type  VARCHAR(20)  NOT NULL DEFAULT '模拟卷',
    rule_config TEXT         DEFAULT NULL,
    question_snapshot TEXT   DEFAULT NULL,
    start_time  TIMESTAMP    DEFAULT NULL,
    end_time    TIMESTAMP    DEFAULT NULL,
    status      TINYINT      DEFAULT 0,
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_record (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    start_time  TIMESTAMP    NOT NULL,
    end_time    TIMESTAMP    DEFAULT NULL,
    score       INT          DEFAULT NULL,
    status      TINYINT      DEFAULT 0,
    ip_address  VARCHAR(50)  DEFAULT NULL,
    -- 人脸核验状态（V3 新增）：开考前必须核验通过、或被教师人工放行
    face_verified    TINYINT      DEFAULT 0,
    face_confidence  DECIMAL(5,2) DEFAULT NULL,
    face_verified_at TIMESTAMP    DEFAULT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exam_user (exam_id, user_id)
);

CREATE TABLE IF NOT EXISTS student_answer (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id   BIGINT       NOT NULL,
    question_id BIGINT       NOT NULL,
    answer      TEXT         DEFAULT NULL,
    score       INT          DEFAULT 0,
    is_correct  TINYINT      DEFAULT 0,
    -- 评分来源：NULL=待复核 / AUTO=客观题自动 / AI=模型评分 / TEACHER=教师复核
    -- 用 graded_by 表达「待复核」，不再拿 is_correct=2 兼职
    -- （is_correct 的语义是 0-错误/1-正确/2-部分正确）
    graded_by   VARCHAR(16)  DEFAULT NULL,
    ai_reason   TEXT         DEFAULT NULL,
    ai_model    VARCHAR(64)  DEFAULT NULL,
    reviewed_by BIGINT       DEFAULT NULL,
    reviewed_at TIMESTAMP    DEFAULT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_record_question (record_id, question_id)
);

-- ============================================================================
--  人脸识别（V3）：底片 + 核验留痕
--  与 db/migration/V3__face_recognition.sql 同结构；H2 侧不用内联 COMMENT 与 FK
--  （沿用本文件既有风格：注释写在列上方，外键约束只在 MySQL 侧建）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_face (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    -- 注册底片：JPEG 小图（前端压到 640×480、质量 0.7）的 base64，约 20-40KB
    image_base64  TEXT         NOT NULL,
    -- 底片摘要：判断底片是否被换过
    image_sha256  VARCHAR(64)  DEFAULT NULL,
    quality_score DECIMAL(5,2) DEFAULT NULL,
    -- ENROLL-注册录入 / SELF-自助补录
    source        VARCHAR(24)  DEFAULT 'ENROLL',
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_face_user (user_id)
);

CREATE TABLE IF NOT EXISTS face_verify_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    exam_id     BIGINT       DEFAULT NULL,
    record_id   BIGINT       DEFAULT NULL,
    -- ENROLL-录入 / EXAM_START-开考前核验 / OVERRIDE-教师放行
    scene       VARCHAR(24)  NOT NULL,
    -- PASS / FAIL / LIVENESS_FAIL / UNAVAILABLE / OVERRIDE_PASS
    result      VARCHAR(16)  NOT NULL,
    -- 厂商返回的相似度 0-100
    confidence  DECIMAL(5,2) DEFAULT NULL,
    -- 1-活体通过 / 0-未通过 / NULL-未检
    liveness    TINYINT      DEFAULT NULL,
    -- 本次开考的第几次尝试（从 1 开始）
    retry_no    INT          DEFAULT 0,
    ip_address  VARCHAR(50)  DEFAULT NULL,
    detail      VARCHAR(255) DEFAULT NULL,
    operator_id BIGINT       DEFAULT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- H2 不支持在 CREATE TABLE 里写 MySQL 的 KEY 索引语法，这里单独建（可重复执行）
CREATE INDEX IF NOT EXISTS idx_face_log_user_exam ON face_verify_log (user_id, exam_id, created_at);
CREATE INDEX IF NOT EXISTS idx_face_log_record ON face_verify_log (record_id);
-- V4：按知识点筛题（题库管理筛选、专项训练抽题都要走它）
CREATE INDEX IF NOT EXISTS idx_question_category ON question (category);