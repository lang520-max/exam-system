-- ============================================================================
--  H2 开发档种子数据（application-dev.yml 引用）
--
--  为什么用 MERGE 而不是 INSERT：
--    dev 档每次启动都会执行 schema + 本文件，普通 INSERT 第二次就会主键冲突，
--    因此这里统一用 MERGE ... KEY(id) 保证幂等，可反复启动。
--
--  演示账号（明文口令均为 123456，库里存 BCrypt 哈希，三个账号哈希各不相同以体现随机盐）：
--    teacher   王老师   教师/管理员
--    student01 李小明   学生
--    student02 张小红   学生（已有一条 80 分的交卷记录用于演示成绩统计）
--
--  题库：单选 4×10 + 多选 2×20 + 判断 2×10 = 100 分，与试卷 1 的题量一致。
--  MySQL 生产环境演示数据见 sql/data.sql（20 道题）。
-- ============================================================================

-- ---------- 用户 ----------
MERGE INTO `user` (id, username, password, nickname, role) KEY (id) VALUES
  (1, 'teacher',   '$2a$10$t1NjtdrAyQX8yWFTJuIuIOC.xNxennhG2kKeuhtn8po.sseXJV5Om', '王老师', 'TEACHER'),
  (2, 'student01', '$2a$10$pEWPMrkq4ZdHRKppTFj2LOrKCBJpVQs.vssCqXm6Q2YhvSEKJZ9oO', '李小明', 'STUDENT'),
  (3, 'student02', '$2a$10$2Y4wBRK3n7p5vFmADUuThem8.VL/vVbSg8uXgSfU2Qef1tbFldZYO', '张小红', 'STUDENT');

-- ---------- 题库 ----------
MERGE INTO question (id, type, subject, knowledge_point, content, option_a, option_b, option_c, option_d, answer, score, difficulty, creator_id) KEY (id) VALUES
  (1, 'SINGLE',   'Java程序设计', '语言基础', '下列哪个关键字用于声明一个不可被继承的类？', 'final', 'static', 'abstract', 'private', 'A', 10.0, 'EASY',   1),
  (2, 'SINGLE',   'Java程序设计', '语言基础', '在 Java 中 int 类型占用多少字节？', '2 字节', '4 字节', '8 字节', '取决于操作系统', 'B', 10.0, 'EASY',   1),
  (3, 'SINGLE',   'Java程序设计', '语言基础', '下列哪一项不是 Java 的基本数据类型？', 'int', 'boolean', 'String', 'char', 'C', 10.0, 'EASY',   1),
  (4, 'SINGLE',   'Java程序设计', '异常处理', '在方法体内主动抛出异常使用的关键字是？', 'throws', 'throw', 'catch', 'finally', 'D', 10.0, 'MEDIUM', 1),
  (5, 'MULTIPLE', 'Java程序设计', '面向对象', '下列属于面向对象三大特性的是？', '封装', '继承', '多态', '编译', 'ABC', 20.0, 'EASY',   1),
  (6, 'MULTIPLE', 'Java程序设计', '集合框架', '下列属于 Java 集合框架顶层接口的有？', 'List', 'Set', 'Map', 'Array', 'ABD', 20.0, 'MEDIUM', 1),
  (7, 'JUDGE',    'Java程序设计', '面向对象', 'Java 中一个类可以同时继承多个父类。', NULL, NULL, NULL, NULL, 'B', 10.0, 'EASY',   1),
  (8, 'JUDGE',    'Java程序设计', '字符串',   'Java 中 String 对象一旦创建，其内容就不可被修改。', NULL, NULL, NULL, NULL, 'A', 10.0, 'EASY',   1);

-- ---------- 试卷 ----------
MERGE INTO exam_paper (id, title, subject, total_score, pass_score, duration_minutes, status, creator_id) KEY (id) VALUES
  (1, '《Java程序设计》期中测试', 'Java程序设计', 100.0, 60.0, 60, 'PUBLISHED', 1),
  (2, '《Java程序设计》随机抽题练习卷', 'Java程序设计', 100.0, 60.0, 30, 'DRAFT', 1);

-- ---------- 试卷题目关联（每题分值快照）----------
MERGE INTO exam_paper_question (paper_id, question_id, score, sort_order) KEY (paper_id, question_id) VALUES
  (1, 1, 10.0, 1), (1, 2, 10.0, 2), (1, 3, 10.0, 3), (1, 4, 10.0, 4),
  (1, 5, 20.0, 5), (1, 6, 20.0, 6), (1, 7, 10.0, 7), (1, 8, 10.0, 8);

-- ---------- 一条已交卷记录：张小红答对前 6 题得 80 分 ----------
MERGE INTO exam_record (id, paper_id, student_id, start_time, submit_time, score,
                        correct_count, total_count, status, is_timeout) KEY (id) VALUES
  (1, 1, 3, '2026-01-15 09:00:00', '2026-01-15 09:52:31', 80.0, 6, 8, 'SUBMITTED', FALSE);

-- ---------- 答题明细：前 6 题答对（用 SELECT 从题库取正确答案，避免手抄出错）----------
MERGE INTO exam_answer (record_id, question_id, student_answer, correct_answer, score, earned_score, is_correct)
  KEY (record_id, question_id)
SELECT 1, pq.question_id, q.answer, q.answer, pq.score, pq.score, TRUE
FROM exam_paper_question pq
JOIN question q ON pq.question_id = q.id
WHERE pq.paper_id = 1 AND pq.sort_order <= 6;

-- 后 2 道判断题故意选反，确保按"全对才得分/精确匹配"规则确实判错
MERGE INTO exam_answer (record_id, question_id, student_answer, correct_answer, score, earned_score, is_correct)
  KEY (record_id, question_id)
SELECT 1, pq.question_id,
       CASE q.answer WHEN 'A' THEN 'B' ELSE 'A' END,
       q.answer, pq.score, 0.0, FALSE
FROM exam_paper_question pq
JOIN question q ON pq.question_id = q.id
WHERE pq.paper_id = 1 AND pq.sort_order >= 7;
