-- ============================================================================
--  在线考试 / 题库系统 —— 演示数据（MySQL 8）
--  先执行 schema.sql，再执行本脚本：mysql -uroot -p exam_system < sql/data.sql
--
--  演示账号（明文口令统一为 123456，库中存的是 BCrypt 哈希）：
--    teacher   / 123456  教师（管理员）
--    student01 / 123456  学生（李小明）
--    student02 / 123456  学生（张小红，已交过一次卷，便于查看成绩统计）
-- ============================================================================

USE `exam_system`;

-- ----------------------------------------------------------------------------
-- 用户：1 名教师 + 2 名学生
-- 哈希由 BCryptPasswordEncoder(强度 10) 生成。注意三个账号的口令都是 123456，
-- 但哈希串各不相同 —— 这正是 BCrypt 自带随机盐的效果，同样的口令不会产生同样的密文。
-- ----------------------------------------------------------------------------
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `role`) VALUES
  (1, 'teacher',   '$2a$10$t1NjtdrAyQX8yWFTJuIuIOC.xNxennhG2kKeuhtn8po.sseXJV5Om', '王老师', 'TEACHER'),
  (2, 'student01', '$2a$10$pEWPMrkq4ZdHRKppTFj2LOrKCBJpVQs.vssCqXm6Q2YhvSEKJZ9oO', '李小明', 'STUDENT'),
  (3, 'student02', '$2a$10$2Y4wBRK3n7p5vFmADUuThem8.VL/vVbSg8uXgSfU2Qef1tbFldZYO', '张小红', 'STUDENT');

-- ----------------------------------------------------------------------------
-- 题库：《Java程序设计》共 20 题
--   单选题 10 题 × 4 分 = 40 分
--   多选题  5 题 × 8 分 = 40 分
--   判断题  5 题 × 4 分 = 20 分
--   合计 100 分（满分为 100 分制）
-- ----------------------------------------------------------------------------
INSERT INTO `question`
  (`id`, `type`, `subject`, `knowledge_point`, `content`, `option_a`, `option_b`, `option_c`, `option_d`, `answer`, `score`, `difficulty`, `creator_id`)
VALUES
  -- ---------- 单选题 ----------
  (1,  'SINGLE', 'Java程序设计', '语言基础',   '下列哪个关键字用于声明一个不可被继承的类？', 'final', 'static', 'abstract', 'private', 'A', 4.0, 'EASY',   1),
  (2,  'SINGLE', 'Java程序设计', '字符串',     '关于 Java 中的 String，下列说法正确的是？', 'String 对象内容可变', 'String 对象内容不可变', 'String 是基本数据类型', 'String 只能存储 ASCII 字符', 'B', 4.0, 'EASY',   1),
  (3,  'SINGLE', 'Java程序设计', '集合框架',   '下列集合类中，方法加了 synchronized 因而线程安全的是？', 'ArrayList', 'HashMap', 'Vector', 'LinkedList', 'C', 4.0, 'MEDIUM', 1),
  (4,  'SINGLE', 'Java程序设计', '语言基础',   '在 Java 中 int 类型占用多少字节？', '2 字节', '4 字节', '8 字节', '取决于操作系统', 'B', 4.0, 'EASY',   1),
  (5,  'SINGLE', 'Java程序设计', '语言基础',   '下列哪一项不是 Java 的基本数据类型？', 'int', 'boolean', 'String', 'char', 'C', 4.0, 'EASY',   1),
  (6,  'SINGLE', 'Java程序设计', '面向对象',   '接口中定义的方法默认具有什么修饰符？', 'public abstract', 'private', 'protected', 'final', 'A', 4.0, 'MEDIUM', 1),
  (7,  'SINGLE', 'Java程序设计', '异常处理',   '在方法体内主动抛出异常使用的关键字是？', 'throws', 'throw', 'catch', 'finally', 'B', 4.0, 'EASY',   1),
  (8,  'SINGLE', 'Java程序设计', 'JVM',        'JVM 中负责回收不再被引用对象的是？', '类加载器', '垃圾回收器', '即时编译器', '字节码校验器', 'B', 4.0, 'EASY',   1),
  (9,  'SINGLE', 'Java程序设计', '并发编程',   '启动一个线程应当调用 Thread 的哪个方法？', 'run()', 'start()', 'execute()', 'begin()', 'B', 4.0, 'MEDIUM', 1),
  (10, 'SINGLE', 'Java程序设计', '集合框架',   'JDK 8 的 HashMap 解决哈希冲突时采用的数据结构是？', '仅链表', '数组 + 链表 + 红黑树', '仅红黑树', 'B+ 树', 'B', 4.0, 'HARD',   1),

  -- ---------- 多选题（全对才得分，少选/多选均不得分）----------
  (11, 'MULTIPLE', 'Java程序设计', '面向对象', '下列属于面向对象三大特性的是？', '封装', '继承', '多态', '编译', 'ABC', 8.0, 'EASY',   1),
  (12, 'MULTIPLE', 'Java程序设计', '字符串',   '关于 String 与 StringBuilder，下列说法正确的有？', 'String 是不可变对象', 'StringBuilder 内容可变', 'StringBuilder 是线程安全的', '循环中频繁拼接字符串建议用 StringBuilder', 'ABD', 8.0, 'MEDIUM', 1),
  (13, 'MULTIPLE', 'Java程序设计', '集合框架', '下列属于 Java 集合框架顶层接口的有？', 'List', 'Set', 'Map', 'Array', 'ABC', 8.0, 'MEDIUM', 1),
  (14, 'MULTIPLE', 'Java程序设计', '异常处理', '关于 Java 异常处理，下列说法正确的有？', 'finally 块常用于释放资源', '捕获异常后可以不处理而继续执行', 'Error 通常不需要被捕获', '一个 try 可以跟多个 catch 分支', 'ACD', 8.0, 'HARD',   1),
  (15, 'MULTIPLE', 'Java程序设计', 'JVM',      '下列属于 JVM 运行时数据区的有？', '堆', '虚拟机栈', '方法区', 'CPU 寄存器组', 'ABC', 8.0, 'MEDIUM', 1),

  -- ---------- 判断题（A=正确，B=错误）----------
  (16, 'JUDGE', 'Java程序设计', '面向对象', 'Java 中一个类可以同时继承多个父类。', NULL, NULL, NULL, NULL, 'B', 4.0, 'EASY',   1),
  (17, 'JUDGE', 'Java程序设计', '面向对象', '接口中定义的成员变量默认是 public static final 的。', NULL, NULL, NULL, NULL, 'A', 4.0, 'MEDIUM', 1),
  (18, 'JUDGE', 'Java程序设计', '语言基础', '一个 Java 源文件中可以定义多个 public 类。', NULL, NULL, NULL, NULL, 'B', 4.0, 'EASY',   1),
  (19, 'JUDGE', 'Java程序设计', '并发编程', 'StringBuilder 是线程安全的，可以在多线程环境下直接共享使用。', NULL, NULL, NULL, NULL, 'B', 4.0, 'MEDIUM', 1),
  (20, 'JUDGE', 'Java程序设计', '字符串',   'Java 中 String 对象一旦创建，其内容就不可被修改。', NULL, NULL, NULL, NULL, 'A', 4.0, 'EASY',   1);

-- ----------------------------------------------------------------------------
-- 试卷：已发布的期中测试（时长 60 分钟、及格分 60）
-- ----------------------------------------------------------------------------
INSERT INTO `exam_paper`
  (`id`, `title`, `subject`, `total_score`, `pass_score`, `duration_minutes`, `status`, `creator_id`)
VALUES
  (1, '《Java程序设计》期中测试', 'Java程序设计', 100.0, 60.0, 60, 'PUBLISHED', 1),
  (2, '《Java程序设计》随机抽题练习卷', 'Java程序设计', 100.0, 60.0, 30, 'DRAFT', 1);

-- 试卷题目：按题库顺序入卷，题号 1~20，分值取题库默认分值（合计 100 分）
INSERT INTO `exam_paper_question` (`paper_id`, `question_id`, `score`, `sort_order`)
SELECT 1, q.id, q.score, q.id
FROM `question` q
WHERE q.subject = 'Java程序设计'
ORDER BY q.id;

-- ----------------------------------------------------------------------------
-- 一份已完成的答卷：张小红（student02）得分 72、答对 14 题、未超时
-- 第 1~14 题答对（10 单选 ×4 + 4 多选 ×8 = 72），第 15~20 题判错
-- 这样教师端「成绩统计」页打开就有数据可看
-- ----------------------------------------------------------------------------
INSERT INTO `exam_record`
  (`id`, `paper_id`, `student_id`, `start_time`, `submit_time`, `score`,
   `correct_count`, `total_count`, `status`, `is_timeout`)
VALUES
  (1, 1, 3, '2025-01-15 09:00:00', '2025-01-15 09:52:31', 72.0, 14, 20, 'SUBMITTED', 0);

-- 答对的 14 题：学生作答与标准答案一致，拿满分
INSERT INTO `exam_answer`
  (`record_id`, `question_id`, `student_answer`, `correct_answer`, `score`, `earned_score`, `is_correct`)
SELECT 1, pq.question_id, q.answer, q.answer, pq.score, pq.score, 1
FROM `exam_paper_question` pq
JOIN `question` q ON pq.question_id = q.id
WHERE pq.paper_id = 1 AND pq.sort_order <= 14;

-- 答错的 6 题：一律选「与正确答案相反」的选项，保证按判分规则确实判错
-- （第 15 题是多选 ABC 却只选了 A，属于「少选不得分」的典型场景）
INSERT INTO `exam_answer`
  (`record_id`, `question_id`, `student_answer`, `correct_answer`, `score`, `earned_score`, `is_correct`)
SELECT 1, pq.question_id,
       CASE q.answer WHEN 'A' THEN 'B' ELSE 'A' END,
       q.answer, pq.score, 0.0, 0
FROM `exam_paper_question` pq
JOIN `question` q ON pq.question_id = q.id
WHERE pq.paper_id = 1 AND pq.sort_order BETWEEN 15 AND 20;
