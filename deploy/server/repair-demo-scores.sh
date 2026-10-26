#!/usr/bin/env bash
# ============================================================================
#  修复演示记录的「客观题得分与试卷快照不一致」（幂等，可重复执行）
#
#  什么时候需要它
#  --------------
#  2026-09-17 把试卷口径从 100 分制改成 150 分制（每卷 150 分 / 及格 90）时，
#  演示记录的**逐题得分**仍是旧口径的值：判分结论 is_correct=1（答对），
#  但 student_answer.score 比试卷快照里的满分小（5 分的单选存成 3 分）。
#
#  后果（第二轮测试报告 Bug 13 的真身）
#  ----------------------------------
#    * 成绩单把答对的题显示成「+3」而不是满分，看起来像「部分正确」；
#    * 「只看错题」按「是否拿到满分」筛，于是**把答对的题留在错题列表里**，
#      用户以为筛选功能坏了（筛选本身是对的，是数据不自洽）。
#
#  修法
#  ----
#  客观题（单选 0 / 多选 1 / 判断 2）：is_correct=1 → 记快照满分，否则 0。
#  主观题（填空 3 / 简答 4）**不动**：那是教师批改给出的部分分，属正常语义。
#  最后按判分口径重算这 4 条记录的总分（= sum(student_answer.score)）。
#
#  用法：bash repair-demo-scores.sh            （需要能免密执行 mysql）
#        bash repair-demo-scores.sh --dry-run  （只看会改什么）
# ============================================================================
set -uo pipefail

DRY=0
[[ ${1:-} == --dry-run ]] && DRY=1

q() { mysql exam_system -e "$1"; }

echo "=== 修复前：4 条演示记录的用时与分数 ==="
q "SELECT id, status, score, start_time, end_time,
          TIMESTAMPDIFF(MINUTE, start_time, end_time) AS mins
   FROM exam_record WHERE id IN (1,2,3,6) ORDER BY id;"

echo "=== 客观题里「答对但没拿到快照满分」的题数（应 > 0，才是真的需要修）==="
q "SELECT q.type, COUNT(*) AS bad_rows, SUM(js.sc - sa.score) AS missing_points
   FROM student_answer sa
   JOIN exam_record r ON r.id = sa.record_id
   JOIN exam e ON e.id = r.exam_id
   JOIN question q ON q.id = sa.question_id
   JOIN JSON_TABLE(e.question_snapshot, '\$[*]'
        COLUMNS (qid BIGINT PATH '\$.questionId', sc INT PATH '\$.score')) js
     ON js.qid = sa.question_id
   WHERE sa.record_id IN (1,2,3,6) AND q.type IN (0,1,2)
     AND sa.is_correct = 1 AND sa.score <> js.sc
   GROUP BY q.type;"

if [[ $DRY -eq 1 ]]; then
  echo "（--dry-run：不执行任何写操作）"
  exit 0
fi

echo "=== 1) 客观题得分对齐快照（只改不一致的行）==="
q "UPDATE student_answer sa
   JOIN exam_record r ON r.id = sa.record_id
   JOIN exam e ON e.id = r.exam_id
   JOIN question q ON q.id = sa.question_id
   JOIN JSON_TABLE(e.question_snapshot, '\$[*]'
        COLUMNS (qid BIGINT PATH '\$.questionId', sc INT PATH '\$.score')) js
     ON js.qid = sa.question_id
   SET sa.score = IF(sa.is_correct = 1, js.sc, 0)
   WHERE q.type IN (0,1,2) AND sa.score <> IF(sa.is_correct = 1, js.sc, 0);"

echo "=== 2) 按判分口径重算这 4 条记录的总分 ==="
q "UPDATE exam_record r
   SET r.score = (SELECT COALESCE(SUM(sa.score),0) FROM student_answer sa WHERE sa.record_id = r.id)
   WHERE r.id IN (1,2,3,6);"

echo "=== 修复后 ==="
q "SELECT id, status, score, start_time, end_time,
          TIMESTAMPDIFF(MINUTE, start_time, end_time) AS mins
   FROM exam_record WHERE id IN (1,2,3,6) ORDER BY id;"

echo "=== 复核：客观题里还有「答对但没拿满分」的吗（应为空）==="
q "SELECT q.type, COUNT(*) AS bad_rows
   FROM student_answer sa
   JOIN exam_record r ON r.id = sa.record_id
   JOIN exam e ON e.id = r.exam_id
   JOIN question q ON q.id = sa.question_id
   JOIN JSON_TABLE(e.question_snapshot, '\$[*]'
        COLUMNS (qid BIGINT PATH '\$.questionId', sc INT PATH '\$.score')) js
     ON js.qid = sa.question_id
   WHERE sa.record_id IN (1,2,3,6) AND q.type IN (0,1,2)
     AND sa.is_correct = 1 AND sa.score <> js.sc
   GROUP BY q.type;"
echo "（上面没有输出行就说明全部对齐了）"
echo
echo "用时（mins）应当**保持不变**：判分收尾已不再改写 end_time（见 5ff71ad）。"