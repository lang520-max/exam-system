-- 演示数据（H2 版）
-- 密码均为 123456 的 BCrypt 哈希（4 个账号 4 个不同哈希串，因为 BCrypt 自带随机盐）
MERGE INTO sys_user (id, username, password, real_name, role, status) KEY(id) VALUES
(1, 'admin',    '$2a$10$Z3bo6YjfFDE6qtIg1UxMpumYKwl1Yik.CKesk/WK4wW8DUN0RlTlu', '管理员', 0, 1),
(2, 'teacher1', '$2a$10$fLCGOswzLnGvbylEwvvvFeFqqL1rWJEWhis8Popf0mXb9vU6XBH5G', '张老师', 1, 1),
(3, 'student01','$2a$10$uzHUtOOSd0Rwjl4eRy8AseeCtDpncugMDkjXmjz6vZm4ZEZanZUNC', '李明',   2, 1),
(4, 'student02','$2a$10$5GSs.vdB239G5U9U7zhAYO.fhrU3cjqhsTVHZIXSh95uqPT1YykBy', '王芳',   2, 1);

MERGE INTO question_bank (id, name, subject, created_by) KEY(id) VALUES
(1, 'Java 基础题库', 'Java', 2),
(2, '数据结构题库', '数据结构', 2);

MERGE INTO question (id, bank_id, type, difficulty, content, options, answer, score, created_by) KEY(id) VALUES
(1, 1, 0, 1, 'Java 中基本数据类型有几个？', '[{"label":"A","content":"4"},{"label":"B","content":"6"},{"label":"C","content":"8"},{"label":"D","content":"10"}]', 'C', 5, 2),
(2, 1, 0, 2, '以下哪个关键字用于实现接口？', '[{"label":"A","content":"extends"},{"label":"B","content":"implements"},{"label":"C","content":"abstract"},{"label":"D","content":"interface"}]', 'B', 5, 2),
(3, 1, 1, 2, '以下哪些是 Java 访问修饰符？', '[{"label":"A","content":"public"},{"label":"B","content":"private"},{"label":"C","content":"protected"},{"label":"D","content":"internal"}]', 'ABC', 10, 2),
(4, 1, 1, 3, '以下哪些是线程安全的数据结构？', '[{"label":"A","content":"ConcurrentHashMap"},{"label":"B","content":"HashMap"},{"label":"C","content":"CopyOnWriteArrayList"},{"label":"D","content":"ArrayList"}]', 'AC', 10, 2),
(5, 1, 2, 1, 'Java 中 String 是基本数据类型。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 5, 2),
(6, 1, 2, 2, '抽象类可以实例化。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 5, 2),
(7, 2, 0, 1, '栈的特点是？', '[{"label":"A","content":"先进先出"},{"label":"B","content":"先进后出"},{"label":"C","content":"随机访问"},{"label":"D","content":"双端进出"}]', 'B', 5, 2),
(8, 2, 0, 2, '哈希表查找的平均时间复杂度是？', '[{"label":"A","content":"O(1)"},{"label":"B","content":"O(log n)"},{"label":"C","content":"O(n)"},{"label":"D","content":"O(n²)"}]', 'A', 5, 2),
(9, 2, 1, 2, '以下哪些是平衡二叉树？', '[{"label":"A","content":"AVL树"},{"label":"B","content":"红黑树"},{"label":"C","content":"二叉搜索树"},{"label":"D","content":"B树"}]', 'AB', 10, 2),
(10, 2, 2, 1, '链表支持随机访问。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 5, 2);

MERGE INTO exam (id, title, description, bank_id, duration, total_score, pass_score, rule_config, question_snapshot, status, created_by) KEY(id) VALUES
(1, 'Java 基础测试', '考察 Java 基础知识', 1, 60, 70, 42, '{"rules":[{"type":0,"count":2,"score":10},{"type":1,"count":2,"score":20},{"type":2,"count":2,"score":5}]}', '[{"questionId":1,"score":10},{"questionId":2,"score":10},{"questionId":3,"score":20},{"questionId":4,"score":20},{"questionId":5,"score":5},{"questionId":6,"score":5}]', 1, 2),
(2, '数据结构摸底', '数据结构基础摸底考试', 2, 45, 50, 30, '{"rules":[{"type":0,"count":2,"score":10},{"type":1,"count":1,"score":20},{"type":2,"count":1,"score":10}]}', '[{"questionId":7,"score":10},{"questionId":8,"score":10},{"questionId":9,"score":20},{"questionId":10,"score":10}]', 1, 2);