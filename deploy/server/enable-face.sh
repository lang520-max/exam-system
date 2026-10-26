#!/usr/bin/env bash
# ============================================================================
#  打开人脸识别门禁（幂等）
#
#  前置：check-face-api.sh 已经验通（RAM 角色能拿到临时凭证、CompareFace 能调通）。
#  为什么坚持先验通：门禁一开，每次开考都要过核验；厂商侧调不通时结果是
#  UNAVAILABLE → 全员被拦在考场外（只能靠教师一个个放行）。所以顺序不能颠倒。
#
#  做三件事：
#    1) 在 /etc/exam-system/exam.env 里把 FACE_ENABLED 设为 true（保留其它变量与权限）
#    2) 重启服务并等 /actuator/health 变 UP
#    3) 打印 /api/face/config 与启动日志里那一行「门禁：已启用」
#
#  用法：sudo bash enable-face.sh          # 打开
#        sudo bash enable-face.sh --off    # 关闭（回滚）
# ============================================================================
set -uo pipefail

ENV_FILE=/etc/exam-system/exam.env
ON=true
[[ ${1:-} == --off ]] && ON=false

log()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

[[ ${EUID:-$(id -u)} -eq 0 ]] || die "请用 root 运行"
[[ -f $ENV_FILE ]] || die "找不到 $ENV_FILE"

log "1/3 设置 FACE_ENABLED=$ON"
cp -a "$ENV_FILE" "$ENV_FILE.bak.$(date +%s)"
if grep -qE '^FACE_ENABLED=' "$ENV_FILE"; then
  sed -i "s/^FACE_ENABLED=.*/FACE_ENABLED=$ON/" "$ENV_FILE"
else
  printf '\n# 人脸识别门禁开关（V3）：true 时注册要采集人脸、开考要先过核验\nFACE_ENABLED=%s\n' "$ON" >> "$ENV_FILE"
fi
grep -E '^FACE_' "$ENV_FILE" | sed 's/^/  /'
chmod 600 "$ENV_FILE"

log "2/3 重启服务并等待健康"
systemctl restart exam-system
for i in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "  就绪（${i} 秒）"
    break
  fi
  sleep 1
  [[ $i -eq 60 ]] && die "90 秒内没起来，看 journalctl -u exam-system -n 50"
done

log "3/3 复核"
echo -n "  /api/face/config → "
curl -s http://127.0.0.1:8080/api/face/config; echo
echo -n "  启动日志 → "
journalctl -u exam-system --no-pager -n 300 2>/dev/null | grep '人脸识别门禁' | tail -1 | sed 's/.*人脸识别门禁/人脸识别门禁/'

if [[ $ON == true ]]; then
  cat <<'TIP'

  门禁已开。注意两件事：
    1) 现有账号（含 4 个演示账号）没有底片 → 开考会提示「去录入人脸」，
       登录后在右上角用户菜单 →「人脸录入」补一次即可。
    2) 摄像头只在 HTTPS / localhost 可用：手机请用 https://<域名或IP>:8443/ 打开。
TIP
else
  echo
  echo "  门禁已关闭（开考不再核验；已录入的底片与留痕都保留）。"
fi