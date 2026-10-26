#!/usr/bin/env bash
# ============================================================================
#  安装 / 升级应用：备份旧 jar → 换上新 jar → 装 systemd 单元 → 重启 → 等健康
#
#  用法：sudo bash deploy-app.sh /path/to/exam-system-2.0.0.jar
#  回滚：sudo bash deploy-app.sh --rollback
# ============================================================================
set -euo pipefail

APP_DIR=/opt/exam-system
JAR="$APP_DIR/app.jar"
JAR_PREV="$APP_DIR/app.jar.prev"
UNIT_SRC="$APP_DIR/deploy/systemd/exam-system.service"
UNIT_DST=/etc/systemd/system/exam-system.service

log() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

[[ ${EUID:-$(id -u)} -eq 0 ]] || die "请用 root 运行：sudo bash deploy-app.sh ..."

if [[ ${1:-} == --rollback ]]; then
  [[ -f $JAR_PREV ]] || die "没有可回滚的 $JAR_PREV"
  log "回滚到上一版 jar"
  install -o exam -g exam -m 640 "$JAR_PREV" "$JAR"
else
  SRC=${1:-/tmp/exam-system.jar}
  [[ -f $SRC ]] || die "找不到新 jar：$SRC"
  log "安装新 jar：$SRC（$(du -h "$SRC" | cut -f1)）"
  install -o exam -g exam -m 640 "$SRC" "$JAR.new"
  [[ -f $JAR ]] && cp -p "$JAR" "$JAR_PREV"
  mv "$JAR.new" "$JAR"
fi

chown exam:exam "$JAR"
chmod 640 "$JAR"

[[ -f $UNIT_SRC ]] || die "找不到 systemd 单元 $UNIT_SRC（deploy/ 目录没传全？）"
install -o root -g root -m 644 "$UNIT_SRC" "$UNIT_DST"

# start.sh 由 bootstrap.sh 生成；这里只校验（unit 的 ExecStart 指向它）
if [[ ! -x $APP_DIR/start.sh ]]; then
  die "缺少可执行的 $APP_DIR/start.sh —— 先跑一次 bootstrap.sh（它负责生成这个包装脚本）"
fi
chown root:root "$APP_DIR/start.sh"
chmod 755 "$APP_DIR/start.sh"

# start.sh 以 exam 身份读 java.env：先验可读性。
# 权限错的表现很绕 —— start.sh 读到空 JAVA_BIN，systemd 那边只报 203/EXEC，
# 看不出是权限问题（2026-09-16 真机踩到一次）。
runuser -u exam -- test -r /etc/exam-system/java.env \
  || die "exam 用户读不到 /etc/exam-system/java.env（应为 root:exam 640，且 /etc/exam-system 需 750 root:exam）"
runuser -u exam -- test -r "$JAR" || die "exam 用户读不到 $JAR"
runuser -u exam -- test -x "$APP_DIR/start.sh" || die "exam 用户无法执行 $APP_DIR/start.sh"

systemctl daemon-reload
systemctl enable exam-system >/dev/null 2>&1 || true

# ---------------------------------------------------------------- 启动前自检
# 为什么要有这一步：如果 java 路径不对、jar 读不到、配置写错，systemd 会以
# 「5 秒一次无限重启」的形式表现（status=203/EXEC / 主进程秒退），
# journal 里只有 systemd 自己的话、看不到 Java 的报错，排查很绕。
# 所以先【以 exam 用户前台跑一次】，把真正的 Java 输出抓下来，再交给 systemd。
log "启动前自检：以 exam 用户前台试跑（最多 60 秒，看 JVM 能否起来）"
systemctl stop exam-system >/dev/null 2>&1 || true
# 注意：GNU mktemp 要求 X 出现在模板【结尾】，写成 xxx.XXXXXX.log 会直接报
# "too few X's in template" 并让脚本退出 —— 所以这里不带后缀。
PRE=$(mktemp /tmp/exam-preflight.XXXXXX)
# 把 exam.env 里的 KEY=VALUE 显式注入子进程。
# 不能指望 `set -a; . exam.env` 之后 runuser 会保留它们：su/runuser 默认【重置环境】，
# 那样自检会因为缺 DB_PASSWORD/JWT_SECRET 而失败，与 systemd 的真实运行不一致
# —— 预检就失去意义了。
# 前提：exam.env 的值内不含空格（bootstrap 生成的都满足：密码是 hex+Aa1!，URL 无空格）。
ENV_ARGS=$(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' /etc/exam-system/exam.env)
(
  # shellcheck disable=SC2086  # 这里就是要按空格拆成多个 KEY=VALUE 参数
  exec runuser -u exam -- /usr/bin/env -i $ENV_ARGS \
    PATH=/usr/sbin:/usr/bin:/sbin:/bin HOME="$APP_DIR" "$APP_DIR/start.sh"
) >"$PRE" 2>&1 &
PRE_PID=$!
for i in $(seq 1 60); do
  # 判据必须【精确】：
  #   成功 —— "Started ExamApplication"
  #   失败 —— Spring Boot 的失败横幅 / Web 容器起不来
  # 曾经用宽泛的 "Exception" 当失败信号，结果 Spring Security 启动日志里那行
  # 过滤器链包含 "ExceptionTranslationFilter"，循环当场退出、误判成自检失败，
  # 把还有 1 秒就启动完成的应用杀掉了（2026-09-16 真机踩到）。
  grep -q 'Started ExamApplication' "$PRE" 2>/dev/null && break
  grep -qE 'APPLICATION FAILED TO START|Error starting ApplicationContext|Web server failed to start|UnsatisfiedDependencyException' "$PRE" 2>/dev/null && break
  sleep 1
done
kill -KILL "$PRE_PID" 2>/dev/null || true
pkill -KILL -u exam -f 'app\.jar' 2>/dev/null || true
sleep 1

if grep -q 'Started ExamApplication' "$PRE" 2>/dev/null; then
  log "自检通过：JVM 正常起来并完成启动（$(grep -c . "$PRE") 行日志）"
  rm -f "$PRE"
else
  printf '\n\033[1;31m自检失败 —— 下面才是真正的报错（systemd 只会告诉你「重启了」）：\033[0m\n' >&2
  # Java 的根因总在输出【末尾】，所以看尾巴而不是开头
  tail -40 "$PRE" >&2
  printf '\n\033[1;33m现场信息：\033[0m\n' >&2
  printf '  start.sh：%s\n' "$(ls -l "$APP_DIR/start.sh" 2>&1)" >&2
  printf '  java.env：%s\n' "$(cat /etc/exam-system/java.env 2>&1 | tr '\n' ' ')" >&2
  printf '  jar：%s\n' "$(ls -l "$JAR" 2>&1)" >&2
  JB=$(sed -n 's/^JAVA_BIN=//p' /etc/exam-system/java.env 2>/dev/null | head -1)
  printf '  java：%s → %s\n' "${JB:-未设置}" "$("${JB:-/bin/false}" -version 2>&1 | head -1)" >&2
  die "应用自检未通过，详见 $PRE 与上面的 Java 输出"
fi

log "交给 systemd 托管"
systemctl restart exam-system

log "等待 /actuator/health（最多 90 秒）"
for i in $(seq 1 90); do
  if curl -fsS --max-time 2 http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    log "应用已就绪（${i} 秒）"
    curl -fsS http://127.0.0.1:8080/actuator/health; echo
    printf '\n内存实况：\n'
    free -m | head -2
    printf 'JVM 常驻：%s MiB\n' "$(ps -o rss= -C java 2>/dev/null | awk '{s+=$1} END{printf "%d", s/1024}')"
    exit 0
  fi
  sleep 1
done

printf '\n\033[1;31m启动超时，最后 60 行日志：\033[0m\n' >&2
journalctl -u exam-system -n 60 --no-pager >&2 || true
printf '\n\033[1;33m诊断：\033[0m\n' >&2
systemctl cat exam-system 2>&1 | sed -n '1,25p' >&2
printf '  %s\n' "$(ls -l "$APP_DIR/start.sh" "$JAR" 2>&1)" >&2
exit 1