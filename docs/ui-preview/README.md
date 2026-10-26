# 界面截图

> 这些图是**纯留档**，没有任何代码或文档依赖它们（随时可删）。

## 当前界面（`cur-*`）

对应 HEAD 的界面现状。出图方式：CDP 连 headless Edge，`Page.captureScreenshot`
（见 `docs/会话交接-项目当前状态.md` §3.6 的验证方式）。

| 文件 | 内容 |
| --- | --- |
| `cur-01-login.png` | 登录页：渐变刊头 + 白色卡片 + 可点击的演示账号（密码可见切换 / 记住用户名 / 字段级报错） |
| `cur-02-sidebar-dropdown.png` | 左侧边栏工作台 + 用户下拉菜单（账号信息 / 退出登录） |
| `cur-03-logout-confirm.png` | 退出登录二次确认弹窗（点「取消」不会退出） |
| `cur-04-teacher-exams.png` | 教师 · 考试管理（6 张四川专升本模拟卷，已发布） |
| `cur-05-import-preview.png` | 导入试卷页：模板已载入，导入前预览 |
| `cur-06-teacher-stats.png` | 成绩统计弹窗：分数分布直方图（按得分率分段）+ 及格率环形 |
| `cur-07-student-charts.png` | 学生 · 我的成绩：得分率趋势折线（含及格线）+ 及格门数环形 |
| `cur-08-sidebar-narrow.png` | 390px 窄屏：侧边栏收成顶部条，菜单横向可滚 |

## 旧版留档（`01-*` ~ `05-*`）

`655beb2` 入库，记录的是**旧版界面**（学术 / 公文风）与**已经删除的高数题库**。
其中两张是 AI 评分的实测证据，仍有意义，所以保留未删：

- `01-real-ai-scores.png` · `01-scores-ai-graded.png` —— 真实调用 AI 评分后的成绩单。
- 其余（`01-login` / `01-student-exam-gaoshu` / `02-teacher-exams` /
  `02-teacher-questions-gaoshu` / `02-teacher-review` / `03-teacher-questions` /
  `04-student-scores` / `05-student-exams`）是旧界面外观，仅作历史对照。