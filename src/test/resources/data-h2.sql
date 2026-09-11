-- ============================================================================
--  H2 测试库演示数据
--  与 sql/data.sql 同构的一份精简版：账号一致，题库缩小到 8 道题（合计 100 分）。
--  这样「页面可渲染」「成绩统计」这类用例有真实数据可断言，
--  同时不会与集成测试自己造的数据互相干扰（测试用例都用独立科目名）。
--
--  演示账号（明文口令均为 123456）：
--    teacher / student01 / student02
-- ============================================================================

INSERT INTO `user` (id, username, password, nickname, role) VALUES
  (1, 'teacher',   '$2a$10$t1NjtdrAyQX8yWFTJuIuIOC.xNxennhG2kKeuhtn8po.sseXJV5Om', '王老师', 'TEACHER'),
  (2, 'student01', '$2a$10$pEWPMrkq4ZdHRKppTFj2LOrKCBJpVQs.vssCqXm6Q2YhvSEKJZ9oO', '李小明', 'STUDENT'),
  (3, 'student02', '$2a$10$2Y4wBRK3n7p5vFmADUuThem8.VL/vVbSg8uXgSfU2Qef1tbFldZYO', '张小红', 'STUDENT');

-- 题库：单选题 4 道 × 10 分 + 多选题 2 道 × 20 分 + 判断题 2 道 × 10 分 = 100 分
INSERT INTO question
  (id, type, subject, knowledge_point, content, option_a, option_b, option_c, option_d, answer, score, difficulty, creator_id)
VALUES
  (1, 'SINGLE',   'Java程序设计', '语言基础', '下列哪个关键字用于声明一个不可被继承的类？', 'final', 'static', 'abstract', 'private', 'A', 10.0, 'EASY',   1),
  (2, 'SINGLE',   'Java程序设计', '语言基础', '在 Java 中 int 类型占用多少字节？', '2 字节', '4 字节', '8 字节', '取决于操作系统', 'B', 10.0, 'EASY',   1),
  (3, 'SINGLE',   'Java程序设计', '语言基础', '下列哪一项不是 Java 的基本数据类型？', 'int', 'boolean', 'String', 'char', 'C', 10.0, 'EASY',   1),
  (4, 'SINGLE',   'Java程序设计', '异常处理', '在方法体内主动抛出异常使用的关键字是？', 'throws', 'throw', 'catch', 'finally', 'D', 10.0, 'MEDIUM', 1),
  (5, 'MULTIPLE', 'Java程序设计', '面向对象', '下列属于面向对象三大特性的是？', '封装', '继承', '多态', '编译', 'ABC', 20.0, 'EASY',   1),
  (6, 'MULTIPLE', 'Java程序设计', '集合框架', '下列属于 Java 集合框架顶层接口的有？', 'List', 'Set', 'Map', 'Array', 'ABD', 20.0, 'MEDIUM', 1),
  (7, 'JUDGE',    'Java程序设计', '面向对象', 'Java 中一个类可以同时继承多个父类。', NULL, NULL, NULL, NULL, 'B', 10.0, 'EASY',   1),
  (8, 'JUDGE',    'Java程序设计', '字符串',   'Java 中 String 对象一旦创建，其内容就不可被修改。', NULL, NULL, NULL, NULL, 'A', 10.0, 'EASY',   1);

-- 已发布的演示试卷（时长 60 分钟、及格分 60）
INSERT INTO exam_paper (id, title, subject, total_score, pass_score, duration_minutes, status, creator_id) VALUES
  (1, '《Java程序设计》期中测试', 'Java程序设计', 100.0, 60.0, 60, 'PUBLISHED', 1),
  (2, '《Java程序设计》随机抽题练习卷', 'Java程序设计', 100.0, 60.0, 30, 'DRAFT', 1);

INSERT INTO exam_paper_question (paper_id, question_id, score, sort_order) VALUES
  (1, 1, 10.0, 1), (1, 2, 10.0, 2), (1, 3, 10.0, 3), (1, 4, 10.0, 4),
  (1, 5, 20.0, 5), (1, 6, 20.0, 6), (1, 7, 10.0, 7), (1, 8, 10.0, 8);

-- ---------------------------------------------------------------------------
-- 张小红（student02）已交卷：答对前 6 题（4 单选 ×10 + 2 多选 ×20 = 80），
-- 后 2 道判断题故意选成与正确答案相反的选项，得 80 分、答对 6 题、未超时。
-- ---------------------------------------------------------------------------
INSERT INTO exam_record (id, paper_id, student_id, start_time, submit_time, score,
                         correct_count, total_count, status, is_timeout) VALUES
  (1, 1, 3, '2025-01-15 09:00:00', '2025-01-15 09:52:31', 80.0, 6, 8, 'SUBMITTED', FALSE);

INSERT INTO exam_answer (record_id, question_id, student_answer, correct_answer, score, earned_score, is_correct)
SELECT 1, pq.question_id, q.answer, q.answer, pq.score, pq.score, TRUE
FROM exam_paper_question pq
JOIN question q ON pq.question_id = q.id
WHERE pq.paper_id = 1 AND pq.sort_order <= 6;

-- 答错的两道判断题：选与正确答案相反的选项，确保按判分规则确实判错
INSERT INTO exam_answer (record_id, question_id, student_answer, correct_answer, score, earned_score, is_correct)
SELECT 1, pq.question_id,
       CASE q.answer WHEN 'A' THEN 'B' ELSE 'A' END,
       q.answer, pq.score, 0.0, FALSE
FROM exam_paper_question pq
JOIN question q ON pq.question_id = q.id
WHERE pq.paper_id = 1 AND pq.sort_order >= 7;
