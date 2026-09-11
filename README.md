# 在线考试 / 题库系统（exam-system）

> 基于 Spring Boot 2.7 + MyBatis + Thymeleaf 的在线考试平台：教师维护题库、手动或随机组卷、
> 发布考试并查看成绩统计；学生在线答题、交卷后**立即自动判分**，并查看成绩单与错题回顾。
>
> 面向的学习/落地场景：课程期中期末考试、企业内部认证测评、培训机构随堂测验。
> 项目刻意不依赖任何前端框架与中间件（无 Redis、无消息队列、无 Docker 强依赖），
> `git clone` 后准备好 MySQL 即可运行，`mvn test` 则完全不依赖 MySQL。

[![Java](https://img.shields.io/badge/Java-1.8-blue.svg)](#技术栈)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](#技术栈)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.5.13-orange.svg)](#技术栈)
[![Tests](https://img.shields.io/badge/tests-65%20passed-success.svg)](#测试说明)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](#许可证)

---

## 目录

- [一、项目简介](#一项目简介)
- [二、在线体验与截图](#二在线体验与截图)
- [三、技术栈](#三技术栈)
- [四、功能清单](#四功能清单)
- [五、数据库表设计](#五数据库表设计)
- [六、核心设计说明](#六核心设计说明)
- [七、本地运行](#七本地运行)
- [八、Docker 部署（可选）](#八docker-部署可选)
- [九、REST API 一览](#九rest-api-一览)
- [十、目录结构](#十目录结构)
- [十一、测试说明](#十一测试说明)
- [十二、后续规划](#十二后续规划)
- [十三、许可证](#十三许可证)

---

## 一、项目简介

### 它解决什么问题

线下考试与「用 Excel + 邮件收作业」的方式有三个绕不开的痛点：

1. **组卷费时**：从题库里挑题、算总分、排题号、抄成卷子，全靠手工；
2. **判分更费时**：几十份卷子逐题批改，多选题还要一个个对答案；选择题、判断题这种**机器一秒能判完**的题占了大量时间；
3. **统计靠手算**：平均分、及格率、最高最低分、错题分布，每次都要重新拉表算一遍。

本项目把这三件事全部自动化：

- **组卷自动化**：既支持教师在题库里手动勾选组卷，也支持「按科目 + 题型 + 数量 + 每题分值」一条规则随机抽题，系统自动算总分、排题号；
- **判分自动化**：学生点「交卷」的瞬间完成判分，单选/判断精确匹配、多选全对才得分；同时落库每一道题的作答、正确答案与得分，形成可追溯的答卷；
- **成绩自动统计**：教师选一场考试就能看到平均分、及格率、最高/最低分与逐人成绩单；学生能看到自己的历史成绩曲线与错题本。

### 项目特点

| 特点 | 说明 |
| --- | --- |
| 判分逻辑可单测 | 核心判分器 `GradeCalculator` 是**无状态纯函数**，不碰数据库、不依赖 Spring 上下文，16 条单元测试毫秒级跑完 |
| 交卷防重复 | 「条件更新 + 唯一约束」双保险，用户连点两次或并发提交都只会判分一次 |
| 答案不外泄 | 考试进行中下发给学生的题目使用独立视图对象，**结构上就不存在** `answer` 字段 |
| 排序防注入 | 所有动态 `ORDER BY` 字段走白名单收敛，非法值回落默认排序 |
| 身份只认 Session | 接口不接受前端传入的 `userId` / `studentId`，越权参数会被服务端直接覆盖 |
| 测试不依赖 MySQL | 集成测试跑 H2（`MODE=MySQL`）内存库，克隆下来 `mvn test` 即可全绿 |
| 中文注释与文档 | 所有注释、页面文案、README 均为中文，注释解释「为什么这么做」而不只是「做了什么」 |

---

## 二、在线体验与截图

> 本项目为本地部署型项目，暂无公网演示环境。

运行起来后访问 <http://localhost:8080>。

### 截图占位

| 页面 | 截图 |
| --- | --- |
| 首页 / 登录页 | `<img src="docs/screenshots/01-login.png" width="720" alt="登录页">` |
| 教师端 · 题库管理 | `<img src="docs/screenshots/02-teacher-questions.png" width="720" alt="题库管理">` |
| 教师端 · 组卷（手动 + 随机） | `<img src="docs/screenshots/03-teacher-paper-new.png" width="720" alt="组卷">` |
| 教师端 · 成绩统计 | `<img src="docs/screenshots/04-teacher-scores.png" width="720" alt="成绩统计">` |
| 学生端 · 可参加的考试 | `<img src="docs/screenshots/05-student-exams.png" width="720" alt="考试列表">` |
| 学生端 · 在线答题（含倒计时） | `<img src="docs/screenshots/06-student-exam.png" width="720" alt="在线答题">` |
| 学生端 · 成绩单与错题回顾 | `<img src="docs/screenshots/07-student-review.png" width="720" alt="错题回顾">` |

> 截图目录尚未提交，你可以自行运行项目后截图补充到 `docs/screenshots/`。

---

## 三、技术栈

| 层次 | 技术 | 版本 | 说明 |
| --- | --- | --- | --- |
| 语言 / 运行时 | Java | **1.8** | 使用 JDK 1.8 编译与运行 |
| 框架 | Spring Boot | **2.7.18** | 2.7 分支的最后一个版本；**不可升到 3.x**，Spring Boot 3 要求 JDK 17 |
| Web | Spring MVC | 5.3.x | REST 接口 + Thymeleaf 页面双通道 |
| 模板引擎 | Thymeleaf | 3.0.x | 服务端渲染，无需 Node 构建 |
| 持久层 | MyBatis | 3.5.13 | `mybatis-spring-boot-starter:2.3.1` |
| 数据库（生产） | MySQL | 8.0.33 | `mysql:mysql-connector-java:8.0.33`（Boot 2.7 未管理该坐标，须显式写版本） |
| 数据库（测试） | H2 | 2.1.214 | `MODE=MySQL` 内存库，集成测试专用 |
| 参数校验 | Hibernate Validator | 6.2.x | `spring-boot-starter-validation` |
| 密码加密 | Spring Security Crypto | 5.7.x | **只引 `spring-security-crypto`**，用 `BCryptPasswordEncoder`；不引 `spring-boot-starter-security` |
| 会话 | HttpSession | — | Session + 自定义注解拦截器鉴权 |
| 前端 | 原生 HTML / CSS / JS | — | 无 jQuery、无 Vue/React，Thymeleaf 渲染 + `fetch` 调 REST |
| 测试 | JUnit 5 + Spring Test + MockMvc | — | 65 个测试方法 |
| 构建 | Maven | 3.9.x | `mvn test` / `mvn spring-boot:run` |
| 部署 | Docker 多阶段构建 + Compose | — | `Dockerfile` + `docker-compose.yml`（app + mysql） |

**依赖版本为什么这么选（踩过的坑）**

1. **Spring Boot 2.7.18 而非 3.x**：Spring Boot 3 的基线是 JDK 17，`java.version=1.8` 会直接编译失败。
2. **只引 `spring-security-crypto`**：如果引入 `spring-boot-starter-security`，Spring Boot 会自动装配
   Security 过滤器链，导致**所有**未带认证信息的请求一律 401，与本项目「Session + 注解拦截器」的鉴权方案冲突。
   我们只需要一个 `BCryptPasswordEncoder`，引单个 crypto 模块即可。
3. **`mysql:mysql-connector-java` 必须显式写版本号**：Spring Boot 2.7 的依赖管理里管理的坐标是
   `com.mysql:mysql-connector-j`，老坐标 `mysql:mysql-connector-java` 不在其中，不写 `<version>` 会 POM 报错。

---

## 四、功能清单

### 教师（`TEACHER`，即管理员）

| 模块 | 功能 |
| --- | --- |
| 账号 | 登录、退出登录 |
| 题库管理 | 新增题目（单选 / 多选 / 判断）、编辑、删除、按关键字/科目/题型/难度分页检索、白名单排序 |
| 题目校验 | 答案必须落在已填写的选项范围内；多选至少两个答案；判断题自动清空选项并把答案归一化为 A/B；被试卷引用的题目禁止删除 |
| 组卷 | **手动组卷**：检索题库 → 勾选题目 → 生成草稿试卷；**随机组卷**：按「科目 + 题型 + 数量 + 每题分值」抽题，题库不足时整卷失败并提示还差几道 |
| 试卷管理 | 试卷列表（按状态筛选）、发布考试、结束考试、删除未考过的试卷 |
| 成绩 | 查看某场考试的成绩列表（含未交卷状态与超时标记）、平均分、及格率、最高分、最低分；查看任一学生的答卷 |

### 学生（`STUDENT`）

| 模块 | 功能 |
| --- | --- |
| 账号 | 注册（只可注册学生）、登录、退出登录 |
| 考试 | 查看可参加的考试列表（含自己在这份卷子上的进度）、开始考试、断线后继续答题（不重置计时） |
| 答题 | 在线答题（服务端渲染题目，刷新不丢题）、答题进度提示、倒计时、到点自动交卷 |
| 交卷 | 交卷即自动判分；超过「开考时间 + 时长」仍可交卷但标记为超时；重复提交被拒绝 |
| 成绩 | 我的成绩列表、个人成绩概览（场次/平均分/及格率/最高最低分）、单场成绩单（逐题作答、正确答案、得分） |
| 错题本 | 只列出答错的题，附标准答案与知识点 |

### 系统级

- 统一响应结构 `Result<T>`、统一分页 `PageResult<T>`、业务异常 `BusinessException` + `GlobalExceptionHandler`
- 基于 `HttpSession` + `@RequireLogin` / `@RequireAdmin` 注解的拦截器鉴权：REST 接口返回 401/403 JSON，页面请求 302 跳登录页并带 `redirect` 回跳地址
- 登录后重建 Session（防会话固定攻击）；BCrypt 存储密码且对外返回的用户对象不带密码
- 静态资源与 `/error` 放行，登录页、注册页、首页匿名可访问

---

## 五、数据库表设计

共 6 张表、58 个字段。建表脚本见 [`sql/schema.sql`](sql/schema.sql)，演示数据见 [`sql/data.sql`](sql/data.sql)。

### 1. `user` —— 用户表（7 字段）

教师与学生共用一张表，通过 `role` 区分。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK AI | 主键 |
| `username` | VARCHAR(20) | 登录名 |
| `password` | VARCHAR(100) | **BCrypt 哈希**，绝不存明文 |
| `nickname` | VARCHAR(20) | 昵称，成绩单上展示 |
| `role` | VARCHAR(16) | `TEACHER` / `STUDENT` |
| `created_at` / `updated_at` | DATETIME | 创建 / 更新时间 |

**索引**

- `UNIQUE uk_user_username (username)`：登录名唯一。Service 层已先查一次给出友好提示，唯一约束用于兜住并发注册同名账号的竞态。
- `KEY idx_user_role (role)`：按角色筛选用户（教师列表 / 学生列表）。

### 2. `question` —— 题目表（15 字段）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK AI | 主键 |
| `type` | VARCHAR(16) | `SINGLE` 单选 / `MULTIPLE` 多选 / `JUDGE` 判断 |
| `subject` | VARCHAR(32) | 科目，随机组卷的抽题维度 |
| `knowledge_point` | VARCHAR(64) | 知识点 / 章节 |
| `content` | VARCHAR(500) | 题干 |
| `option_a` ~ `option_d` | VARCHAR(200) | 选项，判断题为 `NULL` |
| `answer` | VARCHAR(16) | 正确答案：单选/判断形如 `A`，多选形如 `ABD`（字母升序） |
| `score` | DECIMAL(6,1) | 题库默认分值（`DECIMAL` 而非 `DOUBLE`，避免浮点累加误差） |
| `difficulty` | VARCHAR(16) | `EASY` / `MEDIUM` / `HARD` |
| `creator_id` | BIGINT FK → `user.id` | 出题教师 |
| `created_at` / `updated_at` | DATETIME | 时间戳 |

**索引**

- `KEY idx_question_subject_type (subject, type)`：**随机组卷的核心索引**。抽题语句是
  `WHERE subject = ? AND type = ? ORDER BY RAND() LIMIT n`，联合索引让筛选走索引、只在命中集合内做随机排序。
- `KEY idx_question_creator (creator_id)`：教师查看「我出的题」。
- `KEY idx_question_difficulty (difficulty)`：按难度筛选。
- `FOREIGN KEY fk_question_creator`：出题人必须存在。

### 3. `exam_paper` —— 试卷表（10 字段）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK AI | 主键 |
| `title` | VARCHAR(100) | 试卷标题 |
| `subject` | VARCHAR(32) | 科目 |
| `total_score` | DECIMAL(6,1) | 总分，**组卷时按题目分值累加后落库**（不每次实时算） |
| `pass_score` | DECIMAL(6,1) | 及格分，判定规则：`得分 >= pass_score` 即及格 |
| `duration_minutes` | INT | 考试时长（分钟），与开考时间共同决定交卷截止时刻 |
| `status` | VARCHAR(16) | `DRAFT` 草稿 → `PUBLISHED` 已发布 → `CLOSED` 已结束 |
| `creator_id` | BIGINT FK → `user.id` | 组卷教师 |
| `created_at` / `updated_at` | DATETIME | 时间戳 |

**索引**

- `KEY idx_paper_status (status)`：学生端只查 `PUBLISHED`，教师端按状态筛选——这是最常用的过滤条件。
- `KEY idx_paper_creator (creator_id)`：按组卷人筛选。

### 4. `exam_paper_question` —— 试卷题目关联表（5 字段）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK AI | 主键 |
| `paper_id` | BIGINT FK → `exam_paper.id` (ON DELETE CASCADE) | 试卷 |
| `question_id` | BIGINT FK → `question.id` | 题目 |
| `score` | DECIMAL(6,1) | **该题在本卷中的分值快照** |
| `sort_order` | INT | 题号，从 1 开始 |

**索引**

- `UNIQUE uk_paper_question (paper_id, question_id)`：同一道题不能在一份试卷里出现两次。
- `KEY idx_pq_paper_sort (paper_id, sort_order)`：取整卷题目时按试卷查、按题号排序。
- `KEY idx_pq_question (question_id)`：删除题目 / 统计引用次数时反查。

> **为什么冗余存 `score`**：教师在题库里把某题从 4 分改成 6 分后，**已经考过的试卷与历史成绩不应被改写**。
> 分值快照和订单里的「成交价快照」是同一个思路。

### 5. `exam_record` —— 考试记录表（12 字段）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK AI | 主键 |
| `paper_id` | BIGINT FK → `exam_paper.id` | 试卷 |
| `student_id` | BIGINT FK → `user.id` | 考生 |
| `start_time` | DATETIME | 开考时间 |
| `submit_time` | DATETIME | 交卷时间，未交卷为 `NULL` |
| `score` | DECIMAL(6,1) | 得分 |
| `correct_count` | INT | 答对题数 |
| `total_count` | INT | 试卷总题数 |
| `status` | VARCHAR(16) | `ONGOING` 答题中 / `SUBMITTED` 已交卷 |
| `is_timeout` | TINYINT(1) | 是否超时交卷（H2 测试脚本用 `BOOLEAN`，JDBC 上都映射为 `Boolean`） |
| `created_at` / `updated_at` | DATETIME | 时间戳 |

**索引**

- `UNIQUE uk_record_paper_student (paper_id, student_id)`：**防重复开考的第一道保险**——一场考试一个学生只能有一条记录。
- `KEY idx_record_student_status (student_id, status)`：学生查「我的成绩」。
- `KEY idx_record_paper_status (paper_id, status)`：教师查某场考试的成绩与统计（只统计 `SUBMITTED`）。

### 6. `exam_answer` —— 答题明细表（9 字段）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK AI | 主键 |
| `record_id` | BIGINT FK → `exam_record.id` (ON DELETE CASCADE) | 考试记录 |
| `question_id` | BIGINT FK → `question.id` | 题目 |
| `student_answer` | VARCHAR(16) | 学生作答（规范化后），未作答为 `NULL` |
| `correct_answer` | VARCHAR(16) | **判分时的正确答案快照** |
| `score` | DECIMAL(6,1) | 该题满分快照 |
| `earned_score` | DECIMAL(6,1) | 实际得分 |
| `is_correct` | TINYINT(1) | 是否答对 |
| `created_at` | DATETIME | 时间戳 |

**索引**

- `UNIQUE uk_answer_record_question (record_id, question_id)`：**防重复交卷的第二道保险**——一道题在同一份答卷里只能有一条作答。
- `KEY idx_answer_record_correct (record_id, is_correct)`：错题回顾即 `WHERE record_id = ? AND is_correct = FALSE`，复合索引直接命中。
- `KEY idx_answer_question (question_id)`：按题目统计正确率（后续规划）。

> **为什么连正确答案也要快照**：教师事后修改题库答案，不应让已判完的答卷变得自相矛盾。
> 错题回顾里展示的答案，永远是考试当天判分时用的那个。

### 表关系图

```
user ──1:N──> question                    教师出题
user ──1:N──> exam_paper                  教师组卷
user ──1:N──> exam_record                 学生考试
exam_paper ──1:N──> exam_paper_question ──N:1──> question     试卷含题（带分值快照）
exam_paper ──1:N──> exam_record ──1:N──> exam_answer ──N:1──> question   答卷明细（带答案快照）
```

---

## 六、核心设计说明

### 6.1 自动判分规则

判分器是 [`service/GradeCalculator.java`](src/main/java/com/caikailang/examsystem/service/GradeCalculator.java)，
它是一个**无状态纯函数**：不查数据库、不依赖 Spring 容器、不做 I/O。同样的题目与答案，永远得到同样的分数。
因此它可以脱离整个应用被单元测试逐条钉死（16 条用例，毫秒级跑完）。

**规则表**

| 题型 | 判分规则 | 举例 |
| --- | --- | --- |
| `SINGLE` 单选 | 与标准答案**精确匹配**得满分，否则 0 分 | 标准 `A`，学生 `A` → 4 分；学生 `C` → 0 分 |
| `JUDGE` 判断 | 同单选，答案统一归一化为 `A`（正确）/ `B`（错误） | 标准 `A`，学生填「对」「正确」「T」「√」→ 均判对 |
| `MULTIPLE` 多选 | **全对才得分**；少选、多选、错选一律 0 分，不给半分 | 标准 `ABC`，学生 `AB` → **0 分**；`ABCD` → 0 分；`ABC` → 8 分 |
| 未作答 | 0 分，且仍会在答卷里留下一条明细 | `null` / 空白串都按未作答处理 |

**答案规范化**（判分前对标准答案与学生答案做同样的处理）

1. 去首尾空白、转大写；
2. 判断题：`对 / 正确 / 是 / T / TRUE / Y / YES / √` → `A`；`错 / 错误 / 否 / F / FALSE / N / NO / × / X` → `B`；
3. 多选题：剥掉 `", " "、"` 等分隔符，只保留字母，**排序 + 去重**后拼回。
   于是 `"C,A"`、`"a c"`、`"AC"` 三种写法等价；同一选项被勾了两次（`"AAB"`）去重后仍是 `"AB"`；
4. 单选 / 判断：剥掉 `"A."`、`"A、"` 之类的修饰，只留字母。

**整卷判分**：按题目在试卷上的顺序逐题判分，得分与满分分别用 `BigDecimal.add()` 累加。

> **为什么用 `BigDecimal` 而不是 `double`**：考试分数是金额级精度的业务数据。
> `0.1 + 0.2 = 0.30000000000000004` 这类浮点误差会让「满分 100」「及格线 60」的比较出现意外结果。
> 同理，及格判定用 `BigDecimal.compareTo()` 而不是 `equals()`——
> `new BigDecimal("60.0").equals(new BigDecimal("60"))` 是 **false**，用 `equals` 会让「考了 60.0 分」被判成不及格。

**满分 100 分制**：`exam_paper.total_score` 在组卷时按各题分值累加落库。
演示数据的设计是「单选 10 题 × 4 分 + 多选 5 题 × 8 分 + 判断 5 题 × 4 分 = 100 分」。
系统本身不限制总分必须是 100，只要求 `pass_score <= total_score`。

### 6.2 防重复交卷与并发控制

重复交卷可能来自三个地方：用户连点两次「交卷」、前端倒计时与手动点击同时触发、开了两个标签页。
如果只是「先查状态、再更新」，两个并发请求会**都**读到 `ONGOING`，然后**都**去写分数、**都**去插答卷明细——
结果就是分数被后一次覆盖、答卷出现重复行。

本项目用「条件更新（Compare-And-Set）」把判定与写入合并成一条原子语句
（见 [`ExamRecordMapper.xml`](src/main/resources/mapper/ExamRecordMapper.xml)）：

```sql
UPDATE exam_record
   SET status = 'SUBMITTED', submit_time = NOW(), score = #{score}, ...
 WHERE id = #{id}
   AND status = 'ONGOING'      -- ← 关键：只有仍处于「答题中」才允许改成「已交卷」
```

调用方根据**受影响行数**判断是否抢到了交卷权：

```java
int changed = recordMapper.compareAndSetSubmit(toSubmit);
if (changed == 0) {
    throw new BusinessException("该试卷已交卷，请勿重复提交");   // 已被并发请求抢先交卷
}
answerMapper.batchInsert(answers);   // 只有抢到交卷权才写答卷明细
```

行锁由数据库保证原子性：两个并发事务中只有一个能把状态从 `ONGOING` 改成 `SUBMITTED`，
另一个的 `WHERE` 条件不再成立，受影响行数为 0，于是被拒绝，**绝不会重复计分**。

**三道防线**

| 防线 | 机制 | 拦截的异常 |
| --- | --- | --- |
| 1 | 交卷前的状态检查（快速失败，给出友好提示） | 绝大多数重复提交 |
| 2 | 条件更新 `WHERE status = 'ONGOING'` | 并发竞态（真正的兜底） |
| 3 | `UNIQUE(record_id, question_id)` | 万一前两道都被绕过，数据库直接拒绝重复明细 |

**开考同理**

- `UNIQUE(paper_id, student_id)` 保证一场考试一个学生只有一条记录；
- 重复开考直接返回**原记录**而不是新建，因此刷新页面**不会重置考试时长**；
- 已交卷后再开考会抛「你已经参加过这场考试，不能重复开考」；
- 并发双击「开始考试」时，第二次插入会撞唯一约束，代码捕获 `DuplicateKeyException` 后回读已存在的那条记录并返回，把数据库异常转成正常业务流程。

**超时处理**：截止时刻 = `start_time + duration_minutes × 60s`。
交卷时若已超过截止时刻，**不拒绝交卷**（避免学生因网络卡顿丢掉整张卷子），
而是把 `is_timeout` 置为 `true`，教师能在成绩单上看到超时标记。
前端倒计时结束时也会自动触发一次交卷，走的正是同一套判分逻辑。

### 6.3 事务边界

| 场景 | 涉及的表 | 为什么必须在一个事务里 |
| --- | --- | --- |
| 组卷（手动 / 随机） | `exam_paper` + `exam_paper_question` | 试卷主记录写成功但关联题写失败，会留下一张「没有题目的空卷」 |
| 交卷判分 | `exam_record` + `exam_answer` | 状态已改成「已交卷」但答卷明细写失败，成绩单会少题且无法重判 |
| 注册 | `user` | 查重与插入之间需要保持一致性语义 |
| 删除试卷 | `exam_paper_question` + `exam_paper` | 先删关联再删主记录，避免外键约束报错 |

### 6.4 鉴权设计

`HttpSession` + 自定义注解 + 拦截器（[`security/`](src/main/java/com/caikailang/examsystem/security)），
**不引入 Spring Security 过滤器链**：

- `@RequireLogin`：需要登录；
- `@RequireAdmin`：需要教师（管理员）角色；
- `AuthInterceptor` 只对被标注的处理器生效。未登录时 REST 接口返回 `401` JSON，
  页面请求 `302` 到 `/login?redirect=<原地址>`；角色不符返回 `403`。

**「只信任 Session，不信任前端」**：所有接口的身份都来自 `AuthInterceptor.requireUser(session)`，
绝不从请求参数里读 `userId` / `studentId`。例如学生查成绩时，服务端会**强制覆盖**查询条件里的 `studentId`：

```java
public PageResult<ExamRecord> myRecords(ExamRecordQuery query, User student) {
    query.setStudentId(student.getId());   // 忽略前端传入的 studentId
    return pageRecords(query);
}
```

**纵深防御**：拦截器是第一道门，Service 层再校验一次角色。这样将来新增入口
（定时任务、内部调用、别的 Controller）时不会因为漏标注解而失守。

**其他安全细节**

- 登录成功后先 `invalidate()` 旧 Session 再新建（防会话固定攻击 Session Fixation）；
- 登录失败时，用户名不存在与密码错误返回**同一句**提示，避免账号枚举；
- 注册接口只创建 `STUDENT`，不接受前端传入 `role`，防止自封管理员；
- 考试进行中下发的题目用独立视图对象 `ExamQuestionView`，**结构上就没有** `answer` 字段——
  否则学生打开浏览器控制台就能看到标准答案（有专门的测试断言 JSON 里不出现 `answer`）；
- 所有动态 `ORDER BY` 字段走白名单（`QuestionQuery#getSafeSortBy()`、`ExamRecordQuery#getSafeSortBy()`），
  非法值回落默认排序，从根上堵住 ORDER BY 注入；
- 分页参数有上限（题库 50 条/页、成绩 100 条/页），防止超大分页拖垮数据库。

### 6.5 MyBatis 使用约定

- **简单语句用注解，动态 SQL 用 XML**：
  `UserMapper`、`ExamPaperQuestionMapper` 语句简单，全部用注解，因此没有对应的 XML；
  `QuestionMapper`、`ExamPaperMapper`、`ExamRecordMapper`、`ExamAnswerMapper` 含动态条件、
  `<foreach>` 批量插入、`<resultMap>`，全部写在 XML 里。
- **「一个 Mapper 要么全注解、要么全 XML」**：避免混用时漏写 statement，
  运行期才抛 `BindingException: Invalid bound statement (not found)`。
- **显式 `<resultMap>`**：`exam_record.is_timeout`、`exam_answer.is_correct` 这类布尔列
  在自动映射下容易被静默跳过，因此逐列写清楚，不依赖约定。
- **批量插入**：交卷时用一条多值 `INSERT ... VALUES (...),(...),...` 写入全部作答，
  比循环单条插入少几十次数据库往返。

---

## 七、本地运行

### 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 1.8（`JAVA_HOME` 指向 JDK 8） |
| Maven | 3.6+ |
| MySQL | 8.0（仅"方式二"需要；**方式一与 `mvn test` 都不需要**） |

### 方式一：零依赖启动（推荐先跑通，不需要装 MySQL）

项目内置 H2 文件库的 `dev` 档，**不装数据库也能直接跑起来看效果**：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

浏览器访问 <http://localhost:8080>，数据落在 `./data/exam_system.mv.db`，重启后仍在。
想看表数据可访问 <http://localhost:8080/h2-console>（JDBC URL 填
`jdbc:h2:file:./data/exam_system`，用户名 `sa`，密码留空）。

> **注意**：`dev` 档会用 `schema-h2.sql` + `data-h2.sql` 建表灌数据，
> 配置里显式写了 `spring.sql.init.encoding: UTF-8`——H2 读取脚本默认用操作系统字符集，
> 不加这行在 Windows 下中文题干和选项会变成乱码。

### 方式二：使用 MySQL（生产同构）

#### 步骤 1：建库并灌入演示数据

```bash
# 方式一：命令行
mysql -uroot -p < sql/schema.sql          # 建库 exam_system + 6 张表
mysql -uroot -p exam_system < sql/data.sql # 演示数据：3 个账号 + 20 道题 + 2 份试卷

# 方式二：在 MySQL 客户端里依次执行
source sql/schema.sql;
source sql/data.sql;
```

#### 步骤 2：按需修改数据库连接

编辑 [`src/main/resources/application.yml`](src/main/resources/application.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/exam_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root      # ← 改成你的账号
    password: root      # ← 改成你的密码
```

> 也可以用环境变量覆盖，不必改文件：
> `SPRING_DATASOURCE_USERNAME=root`、`SPRING_DATASOURCE_PASSWORD=你的密码`。

### 步骤 3：启动

```bash
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package -DskipTests
java -jar target/exam-system-1.0.0.jar
```

### 步骤 4：访问

| 地址 | 说明 |
| --- | --- |
| <http://localhost:8080> | 首页 / 自动跳转到对应工作台 |
| <http://localhost:8080/login> | 登录页 |
| <http://localhost:8080/register> | 注册学生账号 |
| <http://localhost:8080/teacher/questions> | 教师端 · 题库管理 |
| <http://localhost:8080/student/exams> | 学生端 · 可参加的考试 |

### 演示账号

| 角色 | 用户名 | 密码 | 说明 |
| --- | --- | --- | --- |
| 教师（管理员） | `teacher` | `123456` | 可维护题库、组卷、发布考试、查看成绩统计 |
| 学生 | `student01` | `123456` | 还没考过任何试，适合体验完整答题流程 |
| 学生 | `student02` | `123456` | 已预置一份 **80 分**的答卷，方便直接查看成绩单与错题回顾 |

> 三个账号的口令都是 `123456`，但数据库里存的是**三个不同的 BCrypt 哈希串**——
> 这正是 BCrypt 自带随机盐的效果。`DemoDataPasswordTest` 会自动校验这一点。

**建议的体验路径**

1. 用 `teacher` 登录 → 「题库管理」看题库 → 「组卷」用随机抽题生成一份新卷 → 「试卷管理」发布；
2. 用 `student01` 登录 → 「在线考试」开始考试 → 答题 → 交卷 → 看成绩单与错题回顾；
3. 回到 `teacher` → 「成绩统计」选这场考试 → 看平均分、及格率、最高/最低分。

---

## 八、Docker 部署（可选）

> 本机未安装 Docker，以下文件作为交付物提供，**未在本地实际运行过**。

```bash
docker compose up -d --build
```

- `Dockerfile`：**多阶段构建**。构建阶段用 `maven:3.9-eclipse-temurin-8` 编译出 fat jar，
  运行阶段只保留 `eclipse-temurin:8-jre-alpine`，镜像体积大幅缩小；
  先单独拷贝 `pom.xml` 预热依赖以充分利用层缓存；容器以非 root 用户运行，并内置健康检查。
- `docker-compose.yml`：`app` + `mysql:8.0.33` 两个服务。
  MySQL 首次启动时会自动执行挂载进 `/docker-entrypoint-initdb.d` 的 `schema.sql` 与 `data.sql`；
  `app` 通过 `depends_on: condition: service_healthy` 等待数据库就绪，
  并用环境变量 `SPRING_DATASOURCE_URL` 覆盖连接串指向容器网络内的 `mysql`。

启动后访问 <http://localhost:8080>（MySQL 映射到宿主机 3307 端口，避免与本机 3306 冲突）。

---

## 九、REST API 一览

统一响应结构：

```json
{ "code": 0, "message": "success", "data": { } }
```

`code` 约定：`0` 成功、`400` 业务/参数错误、`401` 未登录、`403` 越权、`404` 资源不存在、`500` 系统异常。

### 认证

| 方法 | 路径 | 说明 | 需登录 | 角色 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/register` | 注册学生账号 | 否 | — |
| POST | `/api/auth/login` | 登录（重建 Session） | 否 | — |
| POST | `/api/auth/logout` | 退出登录 | 是 | 任意 |
| GET | `/api/auth/me` | 当前登录用户（未登录 data 为 null） | 否 | — |

### 题库（教师）

| 方法 | 路径 | 说明 | 需登录 | 角色 |
| --- | --- | --- | --- | --- |
| GET | `/api/questions` | 分页检索题库（关键字/科目/题型/难度/白名单排序） | 是 | 教师 |
| GET | `/api/questions/subjects` | 题库中已有的科目清单 | 是 | 教师 |
| GET | `/api/questions/{id}` | 题目详情 | 是 | 教师 |
| POST | `/api/questions` | 新增题目 | 是 | 教师 |
| PUT | `/api/questions/{id}` | 编辑题目 | 是 | 教师 |
| DELETE | `/api/questions/{id}` | 删除题目（被试卷引用时拒绝） | 是 | 教师 |

### 组卷（教师）

| 方法 | 路径 | 说明 | 需登录 | 角色 |
| --- | --- | --- | --- | --- |
| GET | `/api/papers` | 试卷列表（可按状态筛选） | 是 | 教师 |
| GET | `/api/papers/{id}` | 试卷详情 | 是 | 教师 |
| GET | `/api/papers/{id}/questions` | 试卷题目预览（**含**正确答案） | 是 | 教师 |
| POST | `/api/papers` | 手动选题组卷 | 是 | 教师 |
| POST | `/api/papers/random` | 按规则随机抽题组卷 | 是 | 教师 |
| POST | `/api/papers/{id}/publish` | 发布考试 | 是 | 教师 |
| POST | `/api/papers/{id}/close` | 结束考试 | 是 | 教师 |
| DELETE | `/api/papers/{id}` | 删除试卷（已有学生考过时拒绝） | 是 | 教师 |

### 考试

| 方法 | 路径 | 说明 | 需登录 | 角色 |
| --- | --- | --- | --- | --- |
| GET | `/api/exams` | 可参加的考试列表（含本人进度） | 是 | 任意 |
| POST | `/api/exams/{paperId}/start` | 开始考试（返回/复用考试记录） | 是 | 学生 |
| GET | `/api/exams/records/{recordId}` | 考试记录详情（含截止时间戳） | 是 | 本人 / 教师 |
| GET | `/api/exams/records/{recordId}/questions` | 取题（**不含**正确答案） | 是 | 本人 |
| POST | `/api/exams/submit` | 交卷并自动判分 | 是 | 学生本人 |
| GET | `/api/exams/my` | 我的考试记录 | 是 | 本人 |
| GET | `/api/exams/records/{recordId}/review` | 答卷回顾（逐题作答 + 正确答案 + 得分） | 是 | 本人 / 教师 |
| GET | `/api/exams/records/{recordId}/wrong` | 错题回顾 | 是 | 本人 / 教师 |

### 成绩与统计

| 方法 | 路径 | 说明 | 需登录 | 角色 |
| --- | --- | --- | --- | --- |
| GET | `/api/scores/my` | 我的成绩列表 | 是 | 本人 |
| GET | `/api/scores/my/stats` | 我的成绩概览（场次/平均分/及格率/最高最低） | 是 | 本人 |
| GET | `/api/scores/paper/{paperId}` | 某场考试的成绩列表 | 是 | 教师 |
| GET | `/api/scores/paper/{paperId}/stats` | 某场考试的成绩统计 | 是 | 教师 |

**请求示例**

```bash
# 登录（返回的 JSESSIONID 用于后续请求）
curl -i -c cookie.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"student01","password":"123456"}'

# 开始考试
curl -b cookie.txt -X POST http://localhost:8080/api/exams/1/start

# 交卷（answers 的 key 是题目 ID，多选答案形如 "ABD"）
curl -b cookie.txt -X POST http://localhost:8080/api/exams/submit \
  -H 'Content-Type: application/json' \
  -d '{"recordId":2,"answers":{"1":"A","2":"B","3":"C","4":"D","5":"ABC","6":"ABD","7":"B","8":"A"}}'
```

### 页面路由

| 方法 | 路径 | 页面 | 需登录 | 角色 |
| --- | --- | --- | --- | --- |
| GET | `/` | 首页（已登录跳工作台） | 否 | — |
| GET | `/login` | 登录页 | 否 | — |
| GET | `/register` | 注册页 | 否 | — |
| GET | `/teacher/questions` | 教师端 · 题库管理 | 是 | 教师 |
| GET | `/teacher/papers` | 教师端 · 试卷管理 | 是 | 教师 |
| GET | `/teacher/paper/new` | 教师端 · 组卷 | 是 | 教师 |
| GET | `/teacher/scores` | 教师端 · 成绩统计（`?paperId=`） | 是 | 教师 |
| GET | `/student/exams` | 学生端 · 可参加的考试 | 是 | 任意 |
| GET | `/student/exam` | 学生端 · 在线答题（`?recordId=`） | 是 | 本人 |
| GET | `/student/scores` | 学生端 · 我的成绩 | 是 | 任意 |
| GET | `/student/review` | 学生端 · 成绩单与错题回顾（`?recordId=`） | 是 | 本人 / 教师 |

---

## 十、目录结构

```
exam-system/
├── pom.xml                        # Maven 依赖（Spring Boot 2.7.18 + MyBatis + MySQL + H2）
├── Dockerfile                     # 多阶段构建（maven:jdk8 构建 → temurin:jre8 运行）
├── docker-compose.yml             # app + mysql 一键编排
├── LICENSE                        # MIT License
├── .gitignore
├── README.md
├── sql/
│   ├── schema.sql                 # MySQL 8 建库建表（含索引与外键注释）
│   └── data.sql                   # 演示数据：3 账号 + 20 题 + 2 试卷 + 1 份已交答卷
└── src/
    ├── main/
    │   ├── java/com/caikailang/examsystem/
    │   │   ├── ExamSystemApplication.java      # 启动类（@MapperScan）
    │   │   ├── common/                         # 通用返回与异常
    │   │   │   ├── Result.java                 # 统一响应 { code, message, data }
    │   │   │   ├── PageResult.java             # 统一分页 { list, total, pageNum, pageSize }
    │   │   │   └── BusinessException.java      # 业务异常（带业务码）
    │   │   ├── config/
    │   │   │   └── WebConfig.java              # 注册拦截器 + BCryptPasswordEncoder
    │   │   ├── security/                       # Session + 注解鉴权
    │   │   │   ├── RequireLogin.java
    │   │   │   ├── RequireAdmin.java
    │   │   │   └── AuthInterceptor.java
    │   │   ├── domain/                         # 领域实体（与表对应）
    │   │   │   ├── User.java
    │   │   │   ├── Question.java
    │   │   │   ├── ExamPaper.java
    │   │   │   ├── ExamPaperQuestion.java      # 含 toQuestion() 用于判分
    │   │   │   ├── ExamRecord.java             # 含 deadline / passed 等领域方法
    │   │   │   └── ExamAnswer.java
    │   │   ├── dto/                            # 表单、查询条件与视图对象
    │   │   │   ├── LoginForm.java  RegisterForm.java
    │   │   │   ├── QuestionForm.java  QuestionQuery.java
    │   │   │   ├── PaperForm.java  RandomPaperForm.java
    │   │   │   ├── SubmitForm.java  ExamRecordQuery.java
    │   │   │   ├── ExamQuestionView.java       # 考试中下发（不含正确答案）
    │   │   │   ├── GradeItem.java  GradeResult.java   # 判分结果
    │   │   │   └── ExamStats.java              # 成绩统计
    │   │   ├── mapper/                         # MyBatis 接口
    │   │   │   ├── UserMapper.java                 # 全注解
    │   │   │   ├── ExamPaperQuestionMapper.java    # 全注解
    │   │   │   ├── QuestionMapper.java             # 全 XML
    │   │   │   ├── ExamPaperMapper.java            # 全 XML
    │   │   │   ├── ExamRecordMapper.java           # 全 XML
    │   │   │   └── ExamAnswerMapper.java           # 全 XML
    │   │   ├── service/
    │   │   │   ├── GradeCalculator.java        # ★ 自动判分（纯函数，可单测）
    │   │   │   ├── UserService.java            # 登录 / 注册
    │   │   │   ├── QuestionService.java        # 题库增删改查 + 题型校验
    │   │   │   ├── ExamPaperService.java       # 手动组卷 / 随机组卷 / 发布
    │   │   │   └── ExamService.java            # ★ 开考 / 交卷判分 / 成绩统计
    │   │   └── web/
    │   │       ├── GlobalExceptionHandler.java # 统一异常 → Result
    │   │       ├── PageController.java         # 首页 / 登录 / 注册
    │   │       ├── TeacherPageController.java  # 教师端页面
    │   │       ├── StudentPageController.java  # 学生端页面
    │   │       └── api/                        # REST 控制器
    │   │           ├── AuthApiController.java
    │   │           ├── QuestionApiController.java
    │   │           ├── PaperApiController.java
    │   │           ├── ExamApiController.java
    │   │           └── ScoreApiController.java
    │   └── resources/
    │       ├── application.yml                 # 数据源、MyBatis、Thymeleaf 配置
    │       ├── mapper/                         # MyBatis XML（与接口方法一一对应）
    │       │   ├── QuestionMapper.xml
    │       │   ├── ExamPaperMapper.xml
    │       │   ├── ExamRecordMapper.xml
    │       │   └── ExamAnswerMapper.xml
    │       ├── static/
    │       │   ├── css/style.css               # 全站样式（无 UI 框架）
    │       │   └── js/app.js                   # 原生 JS：调接口 / 倒计时 / 收集答题卡
    │       └── templates/                      # Thymeleaf 模板
    │           ├── index.html  login.html  register.html
    │           ├── fragments/common.html       # 顶部导航与页脚片段
    │           ├── teacher/questions.html  papers.html  paper-new.html  scores.html
    │           └── student/exams.html  exam.html  scores.html  review.html
    └── test/
        ├── java/com/caikailang/examsystem/
        │   ├── GradeCalculatorTest.java        # 16 条判分规则单元测试（不启 Spring）
        │   ├── ExamFlowIntegrationTest.java    # 29 条业务集成测试（H2 + MyBatis + 事务）
        │   ├── WebLayerTest.java               # 16 条 Web 层测试（Thymeleaf 渲染 / 鉴权 / REST）
        │   ├── MapperBindingTest.java          # 2 条 MyBatis 映射自检
        │   └── DemoDataPasswordTest.java       # 2 条演示数据口令自检
        └── resources/
            ├── application-test.yml            # H2 内存库配置
            ├── schema-h2.sql                   # H2 建表（与 MySQL 脚本同构）
            └── data-h2.sql                     # H2 演示数据
```

---

## 十一、测试说明

```bash
mvn test
```

**实测结果：`Tests run: 65, Failures: 0, Errors: 0, Skipped: 0` —— BUILD SUCCESS。**

测试完全跑在 **H2 内存库**（`MODE=MySQL`）上，不依赖本机是否安装 MySQL；
建表与演示数据分别来自 `src/test/resources/schema-h2.sql` 与 `data-h2.sql`。

### 1. `GradeCalculatorTest` —— 16 条纯单元测试（不启动 Spring）

判分规则是考试系统的命门，这 16 条把每一条规则都钉成了断言：

| 用例 | 覆盖点 |
| --- | --- |
| `singleChoice_exactMatch_getsFullScore` | 单选答对得满分 |
| `singleChoice_wrongAnswer_getsZero` | 单选答错 0 分 |
| `singleChoice_ignoresCaseAndWhitespace` | 忽略大小写与首尾空白 |
| `judge_matchesExactOption` | 判断题 A/B 精确匹配 |
| `judge_normalizesChineseAndBooleanText` | 「对/正确/T/√」归一化为 A，「错/错误/F/×」归一化为 B |
| `multiple_allCorrect_getsFullScore` | 多选全对得满分 |
| `multiple_orderDoesNotMatter` | 多选选项顺序无关（`CBA` ≡ `ABC`） |
| **`multiple_partialSelection_getsZero`** | **多选少选不得分** |
| `multiple_extraSelection_getsZero` | **多选多选不得分** |
| `multiple_wrongOrDuplicatedSelection` | 错选不得分；重复勾选去重后仍判对 |
| `blankAnswer_getsZero` | `null` 与空白串按未作答处理 |
| `gradeWholePaper_sumsScoreAndCountsCorrect` | 整卷按分值累加、统计答对题数、未作答也计入总题数 |
| `gradeResult_passJudgement_usesCompareToNotEquals` | 及格判定用 `compareTo`（`60.0` 分应判及格） |
| `questionQuery_sortWhitelistBlocksInjection` | 题库排序字段白名单 + 分页上限 |
| `examRecordQuery_sortWhitelistBlocksInjection` | 成绩排序字段白名单 |
| `examRecord_deadlineAndPassedFlag` | 截止时刻 = 开考时间 + 时长；及格标记 |

### 2. `ExamFlowIntegrationTest` —— 29 条业务集成测试

真实走 Spring 容器 + H2 + MyBatis + 事务，每个用例用独立科目名造数据，互不干扰。

**题库（6 条）**
`createQuestion_shouldRejectStudent`（学生建题 403）、`writeOperations_shouldRejectStudent`（学生编辑/删除/组卷 403）、
`createQuestion_shouldValidateAnswerAgainstOptions`（答案超出选项范围、选项不足、多选答案太少）、
`createQuestion_shouldNormalizeJudge`（判断题清空选项、答案归一化）、
`deleteQuestion_shouldRejectWhenUsedByPaper`（被引用的题不能删）、
`searchQuestions_shouldFilterByKeywordAndType`（关键字/题型/分页/排序注入防护）

**组卷（6 条）**
`createManualPaper_shouldSumTotalScore`（总分累加、题号连续、初始草稿）、
`createManualPaper_shouldRejectInvalidInput`（重复题、及格分高于总分）、
`createRandomPaper_shouldPickRequestedCounts`（抽题数量/题型/总分正确、不重复、同科目）、
`createRandomPaper_shouldFailWhenQuestionsNotEnough`（题目不足时报错并说明差几道）、
`createRandomPaper_shouldRejectDuplicatedTypeRule`（同题型重复规则被拒）、
`publishAndDelete_shouldCheckPreconditions`（非法状态值、已考过不能删、结束后学生端不可见）

**考试与判分（7 条）**
`fullExamFlow_shouldGradeFullScoreAndPass`（全对 100 分且及格）、
`submit_shouldGradePartialScore`（**多选少选丢 20 分得 80，仍及格**）、
`submit_shouldFailWhenScoreBelowPassLine`（20 分不及格、未作答也留明细）、
`submit_shouldMarkTimeoutWhenOverdue`（超时交卷可提交但打标记）、
`submit_shouldRejectDuplicateSubmission`（**重复交卷被拒且不改写分数、不产生重复明细**）、
`startExam_shouldBeIdempotentAndBlockRestart`（重复开考不重置计时）、
`examQuestions_shouldNotLeakCorrectAnswer`（下发题目不含正确答案）

**越权（3 条）**
`studentCannotAccessOthersRecord`（不能交别人的卷、不能看别人的成绩/答卷/错题）、
`paperRecordsAndStats_shouldBeTeacherOnly`（教师专属接口对 403）、
`myRecords_shouldForceSessionStudentId`（**前端传的 studentId 被服务端覆盖**）

**成绩统计（3 条）**
`paperStats_shouldComputeAvgPassRateAndMinMax`（3 人 100/80/20 → 平均 66.7、及格率 66.7%、最高 100、最低 20，未交卷者不计入）、
`paperStats_shouldHandleEmptyPaper`（无人交卷返回 0 而不是除零异常）、
`myStats_shouldAggregateAcrossPapers`（跨试卷汇总）

**其他（4 条）**
`register_shouldHashPassword`（BCrypt 存储、对外不带密码）、
`publishedPapers_shouldExposeStudentProgress`（学生端只列已发布试卷并带出本人进度）、
`startExam_shouldRejectUnpublishedPaper`（草稿不能考）、
`startExam_shouldRejectTeacher`（教师不能参加考试）

### 3. `WebLayerTest` —— 16 条 Web 层测试

**页面渲染（7 条）**：登录页/注册页/首页、教师题库页渲染出题干与出题人、
学生考试列表渲染出已发布试卷与「已交卷 80.0 分」、学生成绩页渲染出平均分、
教师成绩统计页渲染出及格率与考生姓名、错题回顾页渲染出正确答案与题目内容。

**鉴权（5 条）**：未登录调题库写接口 401、未登录调成绩/考试接口 401、
受保护页面 302 到 `/login?redirect=...`、学生调教师专属接口 403 且访问教师页面被重定向、
登出后原 Session 失效再访问返回 401。

**校验与全流程（4 条）**：注册参数不合法返回第一条校验提示、重复用户名被拒 + 密码错误 401、
**REST 全流程**（注册 → 登录 → 看可考列表 → 开考 → 取题并断言不含 `answer` → 交卷得 100 分 → 重复交卷被拒 → 查成绩 → 查答卷/错题/统计）、
教师端接口返回统一 `Result` 结构。

### 4. `MapperBindingTest` —— 2 条 MyBatis 映射自检

这是本项目最"值钱"的两条测试：

- **正向**：遍历 6 个 Mapper 接口的**每一个**方法，逐个到 MyBatis 的 `Configuration` 里查有没有对应的
  mapped statement。只要有人加了方法忘了写 SQL，`mvn test` 立刻失败，
  而不是等到上线后某个接口被点到才抛 `BindingException: Invalid bound statement (not found)`；
- **反向**：检查 XML 里不存在接口中并不存在的方法名（拼写错误）。

### 5. `DemoDataPasswordTest` —— 2 条交付物自检

直接读取 `sql/data.sql` 与 `src/test/resources/data-h2.sql`，用正则提取 BCrypt 哈希，
断言它确实能用演示口令 `123456` 校验通过，并断言脚本里不出现明文口令。
这样 README 里「演示账号 `teacher / 123456`」的承诺就变成了可执行的断言，
而不是一句可能随手写错的话。

### 端到端验收（真实 HTTP 调用）

单元测试跑的是 MockMvc，验证的是"控制器 + 服务 + SQL 拼装"是否正确；
但**真实启动、真实 HTTP、真实 Session Cookie** 这一层还需要单独验收。项目自带脚本：

```bash
# 1) 用 dev 档启动（不需要 MySQL）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 2) 另开一个终端
node scripts/e2e-check.js http://localhost:8080
```

脚本会用真实 HTTP 与 Cookie 完整走一遍 9 个环节、**59 项断言**：

1. 鉴权：未登录 401、教师/学生登录、密码错误
2. 角色隔离：学生不能建题、不能访问题库、不能发布试卷
3. 题库录入：三种题型入库 + 非法题型/越界分值被参数校验拦截
4. 组卷与发布：随机抽题、总分累加、未发布卷子对学生不可见、发布后可见
5. 考试与**答案不外泄**：开考、重复开考返回原记录、下发题目不含答案字段
6. **自动判分规则**：单选答对/答错、多选少选不得分、判断题、总分与对错题数
7. **防重复交卷**：重复交卷被拒且原分数与答题明细不被改写
8. 越权与统计：多人独立开考、不能看他人题目/答卷、成绩统计（平均分/最高最低分/及格率）
9. 页面渲染：教师 3 页 + 学生 4 页可渲染、已交卷记录答题页自动跳成绩回顾、学生访问教师页被拦截

输出 `通过 59 项, 失败 0 项` 即为验收通过。该脚本会真实写库（建题、组卷、交卷），建议只在 dev 档跑。

### 手工验收清单

```bash
# 1. 全量测试
mvn test

# 2. 启动后手工验证
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# - 未登录访问 http://localhost:8080/teacher/questions → 跳转登录页
# - 用 student01 登录后访问教师页面 → 被拒绝
# - 用 teacher 登录 → 组卷 → 发布 → 用 student01 答题 → 交卷 → 看成绩单
```

---

## 十二、后续规划

| 优先级 | 规划 | 说明 |
| --- | --- | --- |
| 高 | 考试防作弊 | 切屏检测与计数、禁止复制粘贴、单点登录（同一账号仅允许一处答题） |
| 高 | 题目批量导入 | 支持 Excel / CSV 模板批量导入题库，导入前做全量校验并给出逐行错误报告 |
| 中 | 题目解析字段 | 为每道题增加「答案解析」，错题回顾时展示为什么选这个答案 |
| 中 | 题目统计 | 基于 `exam_answer` 统计每道题的正确率与区分度，辅助教师淘汰劣质题 |
| 中 | 断点续答 | 答题过程中定时把答题卡存到服务端，换设备也能接着答 |
| 中 | 成绩导出 | 某场考试成绩导出为 Excel / CSV |
| 低 | Redis 缓存 | 缓存题库热点数据与会话，支撑更高并发（当前 Session 为单机内存态） |
| 低 | 试卷与题目版本化 | 记录每次修改的历史版本，支持审计与回滚 |
| 低 | 多租户 / 班级维度 | 引入班级概念，考试成绩按班级维度统计与对比 |
| 低 | 接口文档 | 引入 springdoc-openapi 自动生成 API 文档 |

---

## 十三、许可证

本项目基于 [MIT License](LICENSE) 开源，版权归 **lang520-max** 所有。

```
MIT License

Copyright (c) 2026 lang520-max

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

## 附：常见问题

**Q：为什么不用 Spring Boot 3？**
A：Spring Boot 3 的基线是 JDK 17，本项目按 JDK 1.8 环境交付（`java.version=1.8`），用 3.x 会直接编译失败。

**Q：为什么没有引入 `spring-boot-starter-security`？**
A：本项目的鉴权方案是「HttpSession + 注解拦截器」。引入 starter 会自动装配 Security 过滤器链，
导致所有未带认证信息的请求（包括单元测试里的 MockMvc 请求）一律 401。
我们只需要 BCrypt，因此只引 `spring-security-crypto` 单模块。

**Q：`mvn test` 需要先装 MySQL 吗？**
A：不需要。测试跑 H2 内存库（`MODE=MySQL`），只有 `mvn spring-boot:run` 本地运行才需要 MySQL。

**Q：多选题少选能不能给一半分？**
A：当前规则是**全对才得分**（少选、多选、错选一律 0 分），这是最严格也最常用的口径。
规则集中在一个纯函数里，若要改成"少选给一半分"，只需修改 `GradeCalculator#grade` 并同步调整对应单元测试。

**Q：忘登录密码怎么办？**
A：`sql/data.sql` 里的哈希对应口令 `123456`；重新执行 `sql/data.sql` 即可恢复演示账号。
生产环境应改为由管理员重置密码，而不是把重置入口暴露在页面上。
