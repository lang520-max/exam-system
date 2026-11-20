# 考试系统题库独立审计任务（2024—2026 四川专升本）

> 项目：`lang520-max/exam-system`（Spring Boot 在线考试系统）
> 本任务只针对**考试系统内置题库及其组卷数据**，不是 Stepwise 学习网站。
> 本轮只审计、只输出报告，不修改题目、SQL、数据库、评分逻辑或业务代码。

## 1. 任务目标

独立审计当前考试系统中四川专升本三科题库（大学英语、高等数学、计算机基础）的质量，并以四川省官方考试要求及 2024—2026 年可核实的真题/考后回忆资料作为参照，回答：

1. 当前数据库导入链路里到底有哪些题？JSON、MySQL SQL、H2 SQL、追加脚本和历史更新脚本是否一致？
2. 每道题是否符合对应科目的官方考纲？
3. 每道题与可核实真题的关系是什么：原题匹配、近似转录、真题改编、仅同考点、原创考试风格题、无关或无法验证？
4. 题目、选项、标准答案、解析是否正确、完整、无歧义？
5. 是否有重复题、换皮题、错误题、低价值题、题型/难度失真？
6. 现有固定模拟卷与随机组卷配置是否合理地反映真实考试，而不是仅仅凑够 150 分？

## 2. 先核对项目事实，不要假设

已确认的仓库路径：

- `scripts/data/shengben-english.json`
- `scripts/data/shengben-gaoshu.json`
- `scripts/data/shengben-computer.json`
- `scripts/gen-shengben-sql.js`
- `sql/data-shengben.sql`
- `sql/data-shengben-add.sql`
- `sql/data-shengben-v4-update.sql`
- `sql/data-exam-retune.sql`
- `src/main/resources/data-h2-shengben.sql`
- `src/main/resources/db/migration/`
- 题库管理、题目实体、DTO、导入、组卷和判分相关代码。

初步读取 main 分支发现：

- 三份 JSON 当前各含 100 道题（英语 ID 1101—1200；高数 1001—1100；计算机 1201—1300）。
- `sql/data-shengben.sql` 当前包含 300 条 question INSERT（ID 1001—1300）。
- `sql/data-shengben-add.sql` 当前包含 150 条 question INSERT（1051—1100、1151—1200、1251—1300），但文件头注释出现“每科100题”和“本次导出150题”等描述，需查明其真实作用与历史演进。
- `src/main/resources/data-h2-shengben.sql` 含 300 条 H2 question MERGE。
- `sql/data-shengben-v4-update.sql` 对题目 1253 有选项修正记录，需对照 JSON 和其他导入数据确认最终一致性。
- 仓库 README 描述三科题库与固定模拟卷，须以代码和实际数据为准，不能只相信 README。

以上为初步快照，不是最终结论。请重新读取当前分支的实际文件，校验以上统计是否仍成立。

## 3. 参考考试要求与来源

以四川省教育厅发布的 2024 年三科考试要求为考纲依据。官方要求链接：

- 高等数学：https://edu.sc.gov.cn/scedu/c100495/2021/8/5/0fb9140b78414a36899ae0faf30c30de/files/2.%E5%9B%9B%E5%B7%9D%E7%9C%81%E6%99%AE%E9%80%9A%E9%AB%98%E6%A0%A1%E4%B8%93%E5%8D%87%E6%9C%AC%E3%80%8A%E9%AB%98%E7%AD%89%E6%95%B0%E5%AD%A6%E3%80%8B%E8%80%83%E8%AF%95%E8%A6%81%E6%B1%82.pdf
- 大学英语：https://edu.sc.gov.cn/scedu/c100495/2021/8/5/0fb9140b78414a36899ae0faf30c30de/files/3.%E5%9B%9B%E5%B7%9D%E7%9C%81%E6%99%AE%E9%80%9A%E9%AB%98%E6%A0%A1%E4%B8%93%E5%8D%87%E6%9C%AC%E3%80%8A%E5%A4%A7%E5%AD%A6%E8%8B%B1%E8%AF%AD%E3%80%8B%E8%80%83%E8%AF%95%E8%A6%81%E6%B1%82.pdf
- 计算机基础：https://edu.sc.gov.cn/scedu/c100495/2021/8/5/0fb9140b78414a36899ae0faf30c30de/files/4.%E5%9B%9B%E5%B7%9D%E7%9C%81%E6%99%AE%E9%80%9A%E9%AB%98%E6%A0%A1%E4%B8%93%E5%8D%87%E6%9C%AC%E3%80%8A%E8%AE%A1%E7%AE%97%E6%9C%BA%E5%9F%BA%E7%A1%80%E3%80%8B%E8%80%83%E8%AF%95%E8%A6%81%E6%B1%82.pdf

官方政策边界：

- 2025 实施规定：https://www.sceea.cn/Html/202411/Newsdetail_3985.html
- 2026 实施规定：https://www.sceea.cn/Html/202512/Newsdetail_4597.html
- 2025、2026 规定沿用 2024 考试要求。官方政策只能证明考试科目、制度和考试要求，不能证明某个机构回忆版中的具体题目是官方原题。

真题/回忆版来源入口：

### 2024
- 英语爱真题文字转录：https://www.aipta.com/article/10228.html
- 英语好老师回忆版：https://www.hlsok.com/sc/news/60660.html
- 高数好老师回忆版：https://www.hlsok.com/sc/news/60663.html
- 高数爱真题资源页（下载未确认可读）：https://www.aipta.com/article/10230.html
- 计算机爱真题文字转录：https://www.aipta.com/article/10226.html
- 计算机资源目录（非完整题面）：https://www.yeyulingfeng.com/a/1295325.html

### 2025
- 英语爱真题文字转录：https://www.aipta.com/article/10229.html
- 英语好老师回忆版：https://www.hlsok.com/news/72173.html
- 高数好老师回忆版：https://www.hlsok.com/news/72172.html
- 高数爱真题资源页：https://www.aipta.com/article/10230.html
- 计算机爱真题文字转录：https://www.aipta.com/article/10227.html
- 计算机好老师回忆版：https://www.hlsok.com/news/72174.html
- 2025 多科回忆版图片汇总：https://www.sohu.com/a/906886262_100198890

### 2026
- 英语回忆线索（页面自称考生回忆版，非官方）：https://sjds.net/636654.html
- 英语部分答案线索：https://www.sjds.net/a/479214.html
- 三科资源目录（仅目录/领取入口，不视为取得题面）：https://www.yeyulingfeng.com/a/1373379.html
- 计算机考后白皮书（只有结构/部分考点摘要，不是完整真题）：https://zsb.xdf.cn/202607/15281221.html
- 计算机考前练习（2026-04-14，考试前发布，不能作为真题）：https://zsb.xdf.cn/202604/15158917.html

### 已知冲突必须保留
- 2025 英语完形 52—56：用户扫描件 PDF 的选项缺失；爱真题文字转录存在候选选项，可作为补缺来源，但不得伪称官方原件。
- 2025 计算机第 2 题：用户扫描件记录 B、D 都显示为 Alt；爱真题转录为 A=Tab、B=Alt、C=Shift、D=Ctrl。不得自动选择其中一个覆盖另一个。
- 2025 计算机综合应用题：用户原册第 59、60 题是两个父题，网页转录将子问拆分到 66；不要把父题/子问结构差异误判成题量差异。
- 2024 计算机第 43 题图形/选项不完整，第 59—60 题依赖图表图片；题面缺图时不得判为完整题。
- 2024 高数没有取得可读的双来源完整题面。
- 2026 三科都没有取得可完整逐题核验的可信公开试卷；不得根据考点摘要虚构真题。

证据等级：

- A：官方考纲/政策，只证明制度和范围。
- B：不同发布主体的考后回忆题面相互印证，不能叫官方原卷。
- C：单来源、部分图片、资源目录或题面不完整。
- D：考试类型不符、考前练习冒充真题、明显冲突或不可信来源。

同一机构转载、同一文章被聚合站复制，不算独立来源。用户扫描件是项目内一手比对材料，但不是官方原卷。

## 4. 逐题审计字段

每一道题都必须保留其数据库/JSON 原始 ID，并单独记录：

- question_id
- bank_id / 科目
- 原题型、难度、分值
- 原始题干、选项、答案、解析
- 考纲匹配章节/知识点
- 真题关系：exact_match / near_match / adapted_variant / same_topic_only / original_exam_style / unrelated / unverifiable
- 真题年份、来源 ID、URL、来源等级
- 匹配置信度及具体证据（不能只凭“感觉像”）
- 独立答案审校：verified / probable / conflict / incorrect / unreviewed
- 题目质量：高 / 中 / 低 / 不建议保留
- 重复关系（完全重复、换数字、同能力重复、非重复）
- 问题标签、建议动作

注意：same_topic_only 只表示同考点，不得标为真题改编；真题来源可信度与答案正确性必须分开评估。

## 5. 必做审计项目

### A. 数据一致性审计
- 对比三份 JSON、MySQL 全量 SQL、增量 SQL、H2 SQL、V4 更新脚本、Flyway migration。
- 找出同一 question_id 在不同文件的题干/选项/答案/解析/难度/分值/category 是否一致。
- 追踪 1001—1300 全部题目、追加题目与 ID 边界。
- 核查 data-shengben-add.sql 是否存在注释过期、导入重复、遗漏或 SQL 与生成脚本不一致。
- 核查 1253 修正是否在所有数据源一致。
- 核查模拟卷 question_snapshot 引用的题目是否存在、题目分值合计是否等于 150、考试时长和题目分布是否合理。
- 检查题库初始化与重复导入是否会覆盖人工修订或破坏已存在考试快照。

### B. 逐题内容质量审计
- 数学题独立重算，检查推导、定义域、端点、符号、积分区域、矩阵运算等。
- 英语题检查语法、搭配、语境、唯一答案、阅读依据、翻译/写作评分要求。
- 计算机题检查术语、Office 版本差异、函数、算法、数据库、网络、进制换算等事实准确性。
- 每题检查题干完整、选项合理、答案唯一、解析能否证明答案。
- 发现问题必须引用 question_id 和原文片段，不允许只写概括。

### C. 考纲覆盖
- 按三科官方考试要求列出完整知识点树。
- 每个知识点统计题量、题型、难度、分值。
- 识别缺失、过少、过度集中及明显偏离考纲的内容。
- 计算机办公自动化需特别关注 Word/Excel/PowerPoint 实际应用；英语不能只统计语法单选；高数不能只做极限/导数。

### D. 真题对照
- 先将可读取的回忆题面整理为“真题参照表”，再与 300 道题逐题比较。
- 对 2024、2025 题面逐项比对；2026 只能使用真实可见的有限线索。
- 无法取得题面的来源只作为线索，不能进入 exact_match / near_match。
- 题干表述、选项、解题结构相似才可判为匹配；仅知识点相同只能是 same_topic_only。
- 说明各科真题匹配数量和匹配率时，分母必须清晰：当前考试系统题库题量、已核实真题题量、或考纲知识点数，不可混淆。

### E. 重复与模拟卷质量
- 识别完全重复、仅改数字、改写同模板、能力重复。
- 审查模拟卷是否有合理题型、难度、考点覆盖和时间压力。
- 不得只因为题目总分 150 就宣称符合四川真实试卷结构。
- 考试系统的重点是**正式考试体验与判分可靠性**，不是学习网站的学习路径、Anki、学习计划或课程内容。

## 6. 输出报告（仅新增 docs/audit/）

请新增：

1. `docs/audit/QUESTION_BANK_AUDIT_2024_2026.md`：总报告和三科评价。
2. `docs/audit/QUESTION_MATCH_MATRIX.csv`：每题一行的真题匹配和审校矩阵。
3. `docs/audit/QUESTION_ISSUES.md`：按严重度列出错误、缺失、冲突、重复。
4. `docs/audit/SYLLABUS_COVERAGE.md`：三科考纲覆盖矩阵。
5. `docs/audit/DATA_CONSISTENCY.md`：JSON/SQL/H2/增量脚本/迁移的一致性检查。
6. `docs/audit/EXAM_PAPER_AUDIT.md`：固定模拟卷与随机组卷规则审查。
7. `docs/audit/QUESTION_AUDIT.json`：机器可读逐题审计结果和汇总。

审计报告须记录审计日期、当前 Git commit、扫描文件清单、实际检查数量、未检查数量、方法、来源和限制。

## 7. 严格执行约束

本轮是只读审计：

- 只允许新增 `docs/audit/**` 报告文件。
- 禁止修改 `scripts/data/**`、`scripts/gen-shengben-sql.js`、`sql/**`、`src/main/resources/**`、Java 源码、测试、前端页面、数据库。
- 禁止删除、改写、重编号或补造题目。
- 禁止改答案、解析、分值、难度、题型。
- 禁止自动执行 SQL 或连接生产/演示数据库写入。
- 禁止将回忆题称为官方原卷。
- 禁止将同考点原创题称为真题改编。
- 不得因为某个来源链接存在就假装已经看过其完整内容。
- 不得使用考前练习作为考后真题。
- 不得将学习网站 Stepwise 的任何目录、功能或数据作为本任务审计对象。

如发现需要修复的问题，只写建议，不实施修改。

## 8. 验收标准

交付前必须满足：

1. 300 道题逐题均有审计记录；若无法全部完成，明确给出实际完成数与未完成数。
2. 三份 JSON 的题目总数、ID 范围、题型/难度/答案/解析统计可复现。
3. SQL 与 H2 数据差异有逐 ID 证据。
4. 每个真题匹配结论附具体来源与证据，不以知识点相同冒充题面匹配。
5. 答案错误与解析错误有具体题目 ID、计算/推理证据。
6. 所有报告只新增于 `docs/audit/`。
7. `git diff --stat` 应显示只新增审计报告；任何题库或业务代码改动均视为不合格。
8. 最终汇报：三科题量、答案完整率、解析完整率、错误数、重复数、考纲覆盖缺口、真题匹配数、模拟卷质量问题、未核实项目、建议整改优先级。

**先证明考试系统题库是否可靠，再决定是否整改。当前任务不得修改任何题目或考试系统业务代码。**
