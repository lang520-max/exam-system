-- 演示数据（H2 版）—— dev 档由 spring.sql.init 自动加载（见 application-dev.yml）
--
-- 这里只放「账号」这类手写数据。题库 / 题目 / 试卷是生成出来的，见：
--   src/main/resources/data-h2-shengben.sql
--     （四川专升本：高等数学 / 英语 / 计算机基础，每科 50 题 + 2 张固定题面模拟卷）
-- 两份文件都在 data-locations 列表里，且本文件必须排在前面 ——
-- 题目与试卷的 created_by=2 指向下面的 teacher1。
--
-- 口令均为 123456 的 BCrypt 哈希（4 个账号 4 个不同哈希串，因为 BCrypt 自带随机盐）
MERGE INTO sys_user (id, username, password, real_name, role, status) KEY(id) VALUES
(1, 'admin',    '$2a$10$Z3bo6YjfFDE6qtIg1UxMpumYKwl1Yik.CKesk/WK4wW8DUN0RlTlu', '管理员', 0, 1),
(2, 'teacher1', '$2a$10$fLCGOswzLnGvbylEwvvvFeFqqL1rWJEWhis8Popf0mXb9vU6XBH5G', '张老师', 1, 1),
(3, 'student01','$2a$10$uzHUtOOSd0Rwjl4eRy8AseeCtDpncugMDkjXmjz6vZm4ZEZanZUNC', '李明',   2, 1),
(4, 'student02','$2a$10$5GSs.vdB239G5U9U7zhAYO.fhrU3cjqhsTVHZIXSh95uqPT1YykBy', '王芳',   2, 1);