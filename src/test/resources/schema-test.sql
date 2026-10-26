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
    -- 知识点/专题（V4 新增）
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
    -- 人脸核验状态（V3 新增）：与 schema-h2.sql / V3 迁移保持一致
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
    graded_by   VARCHAR(16)  DEFAULT NULL,
    ai_reason   TEXT         DEFAULT NULL,
    ai_model    VARCHAR(64)  DEFAULT NULL,
    reviewed_by BIGINT       DEFAULT NULL,
    reviewed_at TIMESTAMP    DEFAULT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_record_question (record_id, question_id)
);

-- ============================================================================
--  人脸识别（V3）：底片 + 核验留痕（与 schema-h2.sql 保持同结构）
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_face (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    image_base64  TEXT         NOT NULL,
    image_sha256  VARCHAR(64)  DEFAULT NULL,
    quality_score DECIMAL(5,2) DEFAULT NULL,
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
    scene       VARCHAR(24)  NOT NULL,
    result      VARCHAR(16)  NOT NULL,
    confidence  DECIMAL(5,2) DEFAULT NULL,
    liveness    TINYINT      DEFAULT NULL,
    retry_no    INT          DEFAULT 0,
    ip_address  VARCHAR(50)  DEFAULT NULL,
    detail      VARCHAR(255) DEFAULT NULL,
    operator_id BIGINT       DEFAULT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_face_log_user_exam ON face_verify_log (user_id, exam_id, created_at);
CREATE INDEX IF NOT EXISTS idx_face_log_record ON face_verify_log (record_id);
-- V4：按知识点筛题
CREATE INDEX IF NOT EXISTS idx_question_category ON question (category);