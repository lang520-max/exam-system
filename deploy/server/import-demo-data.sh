#!/usr/bin/env bash
# ============================================================================
#  导入演示数据到生产库（MySQL）
#
#  为什么不能自动：prod 档 spring.sql.init = never（刻意关死，防止把演示数据
#  灌进生产库），Flyway 只建表不灌数据，所以账号与题库必须手工导入。
#
#  幂等：两个 SQL 都会先删掉自己导入过的数据再插（data-shengben.sql 开头有 DELETE）。
#        若学生已作答，外键会阻止删除并报错 —— 这是刻意的，避免静默删掉真实作答。
#
#  执行顺序有意义：先账号（data.sql），再题库（data-shengben.sql 的 created_by 指向 teacher1）。
#  用法：sudo bash import-demo-data.sh
# ============================================================================
set -euo pipefail

SQL_DIR=${SQL_DIR:-/opt/exam-system/sql}
ENV_FILE=/etc/exam-system/exam.env

log() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

[[ -r $ENV_FILE ]] || die "读不到 $ENV_FILE（先跑 bootstrap.sh）"
DB_PASSWORD=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
DB_USERNAME=$(grep -E '^DB_USERNAME=' "$ENV_FILE" | cut -d= -f2-)
[[ -n $DB_PASSWORD ]] || die "exam.env 里没有 DB_PASSWORD"

# 用 MYSQL_PWD 而不是 -p<密码>：否则 mysql 会警告「命令行传密码不安全」，也会进 ps
export MYSQL_PWD="$DB_PASSWORD"
mysql_exam() { mysql -h127.0.0.1 -u"$DB_USERNAME" --default-character-set=utf8mb4 exam_system "$@"; }

for f in data.sql data-shengben.sql; do
  [[ -f "$SQL_DIR/$f" ]] || die "找不到 $SQL_DIR/$f（sql/ 目录没传全？）"
done

log "导入演示账号（sql/data.sql：admin / teacher1 / student01 / student02，口令 123456）"
mysql_exam < "$SQL_DIR/data.sql"

log "导入题库 / 题目 / 试卷（sql/data-shengben.sql）"
mysql_exam < "$SQL_DIR/data-shengben.sql"

log "校验："
mysql_exam -t -e "
SELECT (SELECT COUNT(*) FROM sys_user)      AS 用户,
       (SELECT COUNT(*) FROM question_bank) AS 题库,
       (SELECT COUNT(*) FROM question)      AS 题目,
       (SELECT COUNT(*) FROM exam)          AS 试卷,
       (SELECT COUNT(*) FROM exam WHERE status = 1) AS 已发布试卷;"

log "完成。演示账号口令均为 123456 —— 对外网暴露时请务必改口令或用安全组限 IP（见 docs/部署-云服务器-2C2G.md §7）"