#!/usr/bin/env bash
# ============================================================================
#  exam-system —— Workbench 一键安装（没有 SSH 通道时用这条）
#
#  为什么需要它：安全组没放行 22 时，外部连不进来，只能在控制台的
#  Workbench 终端里跑。整个部署被压成「跑一个脚本 + 把输出贴回来」。
#
#  用法（Workbench 里，root 身份）：
#     bash /root/exam-deploy/install.sh
#
#  幂等：可以重复跑。bootstrap.sh 会复用已有密钥，SQL 会先删自己导入的数据。
#  全程日志同时写入 /opt/exam-system/install.log
# ============================================================================
set -uo pipefail

BUNDLE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
APP_DIR=/opt/exam-system
LOG=$APP_DIR/install.log
JAR_NAME=exam-system-2.0.0.jar

mkdir -p "$APP_DIR" /var/log/exam-system
: > "$LOG"

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

[[ ${EUID:-$(id -u)} -eq 0 ]] || die "请用 root 运行（Workbench 默认就是 root）"
[[ -f "$BUNDLE_DIR/$JAR_NAME" ]] || die "找不到 $BUNDLE_DIR/$JAR_NAME —— 部署包没传全？"

# ---------------------------------------------------------------- 0. 环境侦察
log "0. 环境侦察（先看清机器，再动手）"
. /etc/os-release 2>/dev/null || true
printf '  系统：%s\n' "${PRETTY_NAME:-unknown}"
printf '  内核：%s   架构：%s   核数：%s\n' "$(uname -r)" "$(uname -m)" "$(nproc)"
printf '  主机名：%s\n' "$(hostname)"
free -m | head -2
df -h / | tail -1

if command -v dnf >/dev/null 2>&1; then
  printf '\n  仓库里可装的包版本（只查不装，用来预判 MySQL 8.0/8.4 之类差异）：\n'
  for p in java-17-openjdk-headless mysql-server redis redis6 nginx python3; do
    v=$(dnf -q info --available "$p" 2>/dev/null | awk -F': *' '/^Version/{print $2; exit}')
    printf '    %-32s %s\n' "$p" "${v:-（仓库里没有）}"
  done
fi

# ---------------------------------------------------------------- 1. 铺开文件
log "1. 铺开部署文件到 $APP_DIR"
install -d "$APP_DIR/deploy" "$APP_DIR/sql"
cp -a "$BUNDLE_DIR/deploy/." "$APP_DIR/deploy/"
cp -a "$BUNDLE_DIR/sql/." "$APP_DIR/sql/"
cp -f "$BUNDLE_DIR/$JAR_NAME" /tmp/$JAR_NAME
chmod +x "$APP_DIR/deploy/server/"*.sh "$APP_DIR/deploy/server/smoke.py" 2>/dev/null || true
printf '  deploy/ 与 sql/ 已就位，jar（%s）已放到 /tmp\n' "$(du -h "/tmp/$JAR_NAME" | cut -f1)"

FAILED=""

run_stage() {
  local name="$1"; shift
  log "$name"
  if "$@" 2>&1 | tee -a "$LOG"; then
    return 0
  fi
  warn "$name 失败"
  return 1
}

# ---------------------------------------------------------------- 1.5 语法自检
# 开发机是 Windows、没有 bash，所以把 `bash -n` 放到服务器上做：
# 有语法错会直接带行号打出来，而不是跑到一半才炸在某个奇怪的地方。
log "1.5 脚本语法自检（bash -n）"
SYNTAX_BAD=0
for f in "$APP_DIR/deploy/server/"*.sh "$APP_DIR/deploy/workbench/"*.sh; do
  [[ -f $f ]] || continue
  if ! bash -n "$f" 2>/dev/null; then
    printf '  语法错误：%s\n' "$f"
    bash -n "$f" 2>&1 | sed 's/^/    /'
    SYNTAX_BAD=1
  fi
done
(( SYNTAX_BAD == 0 )) || die "有脚本语法错误，先修再跑（上面已给出行号）"
printf '  全部通过\n'

# ---------------------------------------------------------------- 2. 初始化服务器
run_stage "2. 服务器初始化（swap / JDK17 / MySQL / Redis / Nginx + 2 GiB 调参）" \
  bash "$APP_DIR/deploy/server/bootstrap.sh" || FAILED="bootstrap"

# start.sh 是 bootstrap 生成的，起服务前再验一次它的语法
if [[ -z $FAILED && -f $APP_DIR/start.sh ]]; then
  bash -n "$APP_DIR/start.sh" || { printf '  start.sh 语法错误：\n'; bash -n "$APP_DIR/start.sh" 2>&1 | sed 's/^/    /'; FAILED="start.sh 语法"; }
fi

# ---------------------------------------------------------------- 3. 启动应用
if [[ -z $FAILED ]]; then
  run_stage "3. 安装并启动应用（systemd）" \
    bash "$APP_DIR/deploy/server/deploy-app.sh" "/tmp/$JAR_NAME" || FAILED="deploy-app"
fi

# ---------------------------------------------------------------- 4. 演示数据
if [[ -z $FAILED ]]; then
  run_stage "4. 导入演示数据（4 账号 + 150 题 + 6 张卷）" \
    bash "$APP_DIR/deploy/server/import-demo-data.sh" || FAILED="import-demo-data"
fi

# ---------------------------------------------------------------- 5. 冒烟 + 内存
PUBIP=$(curl -fsS --max-time 5 http://100.100.100.200/latest/meta-data/eipv4 2>/dev/null || true)
[[ -n $PUBIP ]] || PUBIP=127.0.0.1
if [[ -z $FAILED ]]; then
  # 冒烟刻意走公网 IP：这样顺带验证「安全组的 80 是不是真的通到外面」
  run_stage "5. 冒烟测试 + 内存实况（经公网 http://$PUBIP）" \
    env SMOKE_PUBLIC="http://$PUBIP" python3 "$APP_DIR/deploy/server/smoke.py" || FAILED="smoke"
fi

# ---------------------------------------------------------------- 6. 汇总
if [[ -n $FAILED ]]; then
  log "失败于：$FAILED —— 最后 40 行日志"
  tail -40 "$LOG"
  cat <<TIP

  排错提示：
    - 完整日志：      less $LOG
    - 服务日志：      journalctl -u exam-system -n 100 --no-pager
    - 修好后重跑：    bash $BUNDLE_DIR/install.sh   （幂等，不会重复生成密钥）
TIP
  exit 1
fi

log "6. 全部完成"
printf '  服务状态：\n'
for s in mysqld mysql redis redis-server nginx exam-system; do
  if systemctl list-unit-files --type=service 2>/dev/null | awk '{print $1}' | grep -qx "$s.service"; then
    printf '    %-16s %s\n' "$s" "$(systemctl is-active "$s" 2>/dev/null)"
  fi
done
printf '\n  健康检查：%s\n' "$(curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health 2>/dev/null || echo '取不到')"
printf '  数据条数：\n'
MYSQL_PWD=$(grep -E '^DB_PASSWORD=' /etc/exam-system/exam.env | cut -d= -f2-) \
  mysql -h127.0.0.1 -u"$(grep -E '^DB_USERNAME=' /etc/exam-system/exam.env | cut -d= -f2-)" \
  --default-character-set=utf8mb4 -t exam_system -e "
SELECT (SELECT COUNT(*) FROM sys_user) AS 用户,
       (SELECT COUNT(*) FROM question_bank) AS 题库,
       (SELECT COUNT(*) FROM question) AS 题目,
       (SELECT COUNT(*) FROM exam) AS 试卷;" 2>/dev/null | tee -a "$LOG"

cat <<TIP

  ─────────────────────────────────────────────────────────────
  访问地址：  http://$PUBIP/
  演示账号：  admin / teacher1 / student01 / student02   口令均为 123456
  密钥位置：  /etc/exam-system/exam.env（0600，MySQL/Redis/JWT 全在这里，勿外传）
  运维命令：  systemctl status exam-system ｜ journalctl -u exam-system -f ｜ free -m
  完整日志：  $LOG
  ─────────────────────────────────────────────────────────────
TIP