-- ============================================================================
--  演示数据（MySQL）
--
--  这里只放「账号」这类手写数据。题库 / 题目 / 试卷见 sql/data-shengben.sql
--  （四川专升本：高等数学 / 英语 / 计算机基础，每科 50 题 + 2 张固定题面模拟卷，
--    由 scripts/gen-shengben-sql.js 生成）。
--
--  执行顺序：先本文件，再 data-shengben.sql —— 后者的 created_by 指向 teacher1。
-- ============================================================================

USE `exam_system`;

-- 密码均为 123456 的 BCrypt 哈希（4 个账号 4 个不同哈希串，因为 BCrypt 自带随机盐）
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role`, `status`) VALUES
('admin',    '$2a$10$Z3bo6YjfFDE6qtIg1UxMpumYKwl1Yik.CKesk/WK4wW8DUN0RlTlu', '管理员', 0, 1),
('teacher1', '$2a$10$fLCGOswzLnGvbylEwvvvFeFqqL1rWJEWhis8Popf0mXb9vU6XBH5G', '张老师', 1, 1),
('student01','$2a$10$uzHUtOOSd0Rwjl4eRy8AseeCtDpncugMDkjXmjz6vZm4ZEZanZUNC', '李明',   2, 1),
('student02','$2a$10$5GSs.vdB239G5U9U7zhAYO.fhrU3cjqhsTVHZIXSh95uqPT1YykBy', '王芳',   2, 1);