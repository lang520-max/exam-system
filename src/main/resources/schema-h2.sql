-- ============================================================================
--  H2 测试库建表脚本
--  与 sql/schema.sql 保持同构，仅去掉 MySQL 专有语法（ENGINE / COMMENT /
--  ON UPDATE CURRENT_TIMESTAMP / TINYINT(1)），供集成测试使用。
--  H2 的 BOOLEAN 与 MySQL 的 TINYINT(1) 在 JDBC 上都映射为 Java Boolean。
-- ============================================================================
DROP TABLE IF EXISTS exam_answer;
DROP TABLE IF EXISTS exam_record;
DROP TABLE IF EXISTS exam_paper_question;
DROP TABLE IF EXISTS exam_paper;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  username   VARCHAR(20)  NOT NULL,
  password   VARCHAR(100) NOT NULL,
  nickname   VARCHAR(20)  NOT NULL,
  role       VARCHAR(16)  NOT NULL DEFAULT 'STUDENT',
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_user_username UNIQUE (username)
);
CREATE INDEX idx_user_role ON `user` (role);

CREATE TABLE question (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  type            VARCHAR(16)   NOT NULL,
  subject         VARCHAR(32)   NOT NULL,
  knowledge_point VARCHAR(64)            DEFAULT NULL,
  content         VARCHAR(500)  NOT NULL,
  option_a        VARCHAR(200)           DEFAULT NULL,
  option_b        VARCHAR(200)           DEFAULT NULL,
  option_c        VARCHAR(200)           DEFAULT NULL,
  option_d        VARCHAR(200)           DEFAULT NULL,
  answer          VARCHAR(16)   NOT NULL,
  score           DECIMAL(6, 1) NOT NULL DEFAULT 5.0,
  difficulty      VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM',
  creator_id      BIGINT        NOT NULL,
  created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_question_creator FOREIGN KEY (creator_id) REFERENCES `user` (id)
);
CREATE INDEX idx_question_subject_type ON question (subject, type);
CREATE INDEX idx_question_creator ON question (creator_id);
CREATE INDEX idx_question_difficulty ON question (difficulty);

CREATE TABLE exam_paper (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  title            VARCHAR(100)  NOT NULL,
  subject          VARCHAR(32)   NOT NULL,
  total_score      DECIMAL(6, 1) NOT NULL,
  pass_score       DECIMAL(6, 1) NOT NULL,
  duration_minutes INT           NOT NULL,
  status           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
  creator_id       BIGINT        NOT NULL,
  created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_paper_creator FOREIGN KEY (creator_id) REFERENCES `user` (id)
);
CREATE INDEX idx_paper_status ON exam_paper (status);
CREATE INDEX idx_paper_creator ON exam_paper (creator_id);

CREATE TABLE exam_paper_question (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  paper_id    BIGINT        NOT NULL,
  question_id BIGINT        NOT NULL,
  score       DECIMAL(6, 1) NOT NULL,
  sort_order  INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_paper_question UNIQUE (paper_id, question_id),
  CONSTRAINT fk_pq_paper FOREIGN KEY (paper_id) REFERENCES exam_paper (id) ON DELETE CASCADE,
  CONSTRAINT fk_pq_question FOREIGN KEY (question_id) REFERENCES question (id)
);
CREATE INDEX idx_pq_paper_sort ON exam_paper_question (paper_id, sort_order);
CREATE INDEX idx_pq_question ON exam_paper_question (question_id);

CREATE TABLE exam_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  paper_id      BIGINT        NOT NULL,
  student_id    BIGINT        NOT NULL,
  start_time    TIMESTAMP     NOT NULL,
  submit_time   TIMESTAMP              DEFAULT NULL,
  score         DECIMAL(6, 1) NOT NULL DEFAULT 0.0,
  correct_count INT           NOT NULL DEFAULT 0,
  total_count   INT           NOT NULL DEFAULT 0,
  status        VARCHAR(16)   NOT NULL DEFAULT 'ONGOING',
  is_timeout    BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_record_paper_student UNIQUE (paper_id, student_id),
  CONSTRAINT fk_record_paper FOREIGN KEY (paper_id) REFERENCES exam_paper (id),
  CONSTRAINT fk_record_student FOREIGN KEY (student_id) REFERENCES `user` (id)
);
CREATE INDEX idx_record_student_status ON exam_record (student_id, status);
CREATE INDEX idx_record_paper_status ON exam_record (paper_id, status);

CREATE TABLE exam_answer (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  record_id      BIGINT        NOT NULL,
  question_id    BIGINT        NOT NULL,
  student_answer VARCHAR(16)            DEFAULT NULL,
  correct_answer VARCHAR(16)   NOT NULL,
  score          DECIMAL(6, 1) NOT NULL,
  earned_score   DECIMAL(6, 1) NOT NULL,
  is_correct     BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_answer_record_question UNIQUE (record_id, question_id),
  CONSTRAINT fk_answer_record FOREIGN KEY (record_id) REFERENCES exam_record (id) ON DELETE CASCADE,
  CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES question (id)
);
CREATE INDEX idx_answer_record_correct ON exam_answer (record_id, is_correct);
CREATE INDEX idx_answer_question ON exam_answer (question_id);
