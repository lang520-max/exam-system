#!/usr/bin/env bash
# ============================================================================
#  人脸 API 连通性诊断 —— 在把 FACE_ENABLED 打开【之前】跑这一条。
#
#  为什么必须先跑：app.face.enabled=true 之后，开考门禁就生效了。若这时
#  RAM 角色没绑好、或 CompareFace 没开通/欠费，每次核验都会返回 UNAVAILABLE，
#  **全校考生都开不了考**。所以先在这里把链路验通，再开开关。
#
#  它做的事：
#    1) 读实例元数据，确认有没有 RAM 角色、临时凭证能不能拿到（不打印密钥）
#    2) 用那份凭证按阿里云 RPC 规范签名，真实调用一次 CompareFace
#    3) 打印厂商原始响应（含 RequestId / Confidence 或错误码）
#
#  用法：sudo bash check-face-api.sh [地域]     默认 cn-shanghai
# ============================================================================
set -uo pipefail

REGION="${1:-cn-shanghai}"
ENDPOINT="https://facebody.${REGION}.aliyuncs.com"
IMDS="http://100.100.100.200/latest/meta-data/ram/security-credentials"

log()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

command -v openssl >/dev/null 2>&1 || die "需要 openssl"
command -v curl    >/dev/null 2>&1 || die "需要 curl"

# ---------------------------------------------------------------- 1. 实例元数据
log "1/3 检查实例 RAM 角色（$IMDS）"
ROLE=$(curl -fsS --max-time 5 "$IMDS/" 2>/dev/null || true)
if [[ -z $ROLE ]]; then
  die "这台实例没有绑定 RAM 角色。两步都容易踩错，按官方文档来：
    1) 建角色时【信任主体类型 = 云服务】，【信任主体名称 = 云服务器 ECS】——
       选成「阿里云账号」建出来的角色，ECS 的「授予/收回RAM角色」下拉框里根本不会出现
       （那个框只列信任策略允许 ecs.aliyuncs.com 扮演的角色）。
       信任策略应当是：{\"Principal\":{\"Service\":[\"ecs.aliyuncs.com\"]}}。
    2) 授予路径：实例 → 右上角「⋯」全部操作 → 实例设置 → 授予/收回RAM角色 →
       操作类型「授予」→ 选角色 → 确定。授予后【无需重启实例】。
    验一下：curl http://100.100.100.200/latest/meta-data/ram/security-credentials/
    （返回角色名=成功；404=没绑上；连不上=安全组/防火墙拦了 100.100.100.200）"
fi
ROLE=$(printf '%s' "$ROLE" | tr -d '\r\n')
log "角色名：$ROLE"

CRED=$(curl -fsS --max-time 5 "$IMDS/$ROLE" 2>/dev/null || true)
[[ -n $CRED ]] || die "拿到了角色名但取不到临时凭证（IMDS 异常？）"

json_get() { printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"; }
AK=$(json_get "$CRED" AccessKeyId)
SK=$(json_get "$CRED" AccessKeySecret)
TOKEN=$(json_get "$CRED" SecurityToken)
EXPIRE=$(json_get "$CRED" Expiration)
[[ -n $AK && -n $SK ]] || die "临时凭证解析失败（原始响应：$(printf '%s' "$CRED" | head -c 200)）"
log "取到临时凭证：AccessKeyId=${AK:0:8}****（不打印密钥），过期时间=$EXPIRE"

# ---------------------------------------------------------------- 2. 构造签名
log "2/3 按阿里云 RPC 规范签名"
# 两张极小的合法 JPEG（1×1 像素），只为验证链路，不做真实比对
IMG_TINY="/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q=="

# 阿里云要求的百分号编码：空格→%20、*→%2A、~ 保持
enc() {
  python3 - "$1" <<'PY'
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe='-_.~').replace('*', '%2A'), end='')
PY
}

TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)
NONCE=$(cat /proc/sys/kernel/random/uuid)

# 所有参数先进数组，**再按 key 排序**拼 canonical —— SecurityToken 也必须参与排序。
# （先前是手工拼串并把 SecurityToken 塞在最前面，于是用 STS 临时凭证时签名必然不匹配）
PARAMS=(
  "AccessKeyId=$AK"
  "Action=CompareFace"
  "Format=JSON"
  "ImageDataA=$IMG_TINY"
  "ImageDataB=$IMG_TINY"
  "RegionId=$REGION"
  "SignatureMethod=HMAC-SHA1"
  "SignatureNonce=$NONCE"
  "SignatureVersion=1.0"
  "Timestamp=$TS"
  "Version=2019-12-30"
)
[[ -n $TOKEN ]] && PARAMS+=("SecurityToken=$TOKEN")

CANON=$(printf '%s\n' "${PARAMS[@]}" | LC_ALL=C sort | while IFS= read -r kv; do
  printf '%s=%s&' "$(enc "${kv%%=*}")" "$(enc "${kv#*=}")"
done)
CANON=${CANON%&}
STS="POST&$(enc '/')&$(enc "$CANON")"
SIG=$(printf '%s' "$STS" | openssl dgst -sha1 -hmac "${SK}&" -binary | openssl base64 -A)
log "签名完成（Signature=${SIG:0:12}****，参与签名 ${#PARAMS[@]} 个参数）"

# ---------------------------------------------------------------- 3. 真实调用
log "3/3 调用 CompareFace（$ENDPOINT）"
BODY_ARGS=()
for kv in "${PARAMS[@]}" "Signature=$SIG"; do
  BODY_ARGS+=(--data-urlencode "$kv")
done

RESP=$(curl -sS --max-time 20 -X POST -H 'Content-Type: application/x-www-form-urlencoded' "${BODY_ARGS[@]}" "$ENDPOINT" 2>&1)
echo
echo "厂商响应："
printf '%s\n' "$RESP" | head -c 1200
echo

if printf '%s' "$RESP" | grep -q '"Confidence"'; then
  echo
  echo "✅ 链路通畅：凭 RAM 角色临时凭证成功调用了 CompareFace。"
  echo "   现在可以打开人脸门禁了：在 /etc/exam-system/exam.env 里设 FACE_ENABLED=true，"
  echo "   然后 systemctl restart exam-system。"
elif printf '%s' "$RESP" | grep -qE 'InvalidAccessKeyId|SignatureDoesNotMatch|Forbidden|Unauthorized'; then
  echo
  echo " 鉴权失败：角色权限不对（策略里是否允许 viapi-facebody:CompareFace？）或服务未开通。"
elif printf '%s' "$RESP" | grep -qE 'Throttling|Limited|quota|欠费|Arrears'; then
  echo
  echo "❌ 被限流/欠费：到「视觉智能开放平台」控制台确认服务已开通且有可用额度。"
elif printf '%s' "$RESP" | grep -qE 'InvalidParameter|InvalidImage|Image'; then
  echo
  echo "⚠ 鉴权已通过（能到业务校验层），但示例图被拒：说明链路是通的，"
  echo "   真实抓拍（640×480 的人脸照）不会有这个问题。可以开门禁。"
else
  echo
  echo " 未识别的响应，请把上面这段原文发我。"
fi