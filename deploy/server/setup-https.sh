#!/usr/bin/env bash
# ============================================================================
#  exam-system：给 8443 端口加 HTTPS（人脸识别必须的安全上下文）
#
#  为什么必须 HTTPS：浏览器的 getUserMedia（摄像头）只在「安全上下文」可用 ——
#  https 或 localhost。http://<公网IP> 上点采集会被浏览器直接拒绝，跟代码无关。
#
#  为什么用 8443 而不是 443：大陆阿里云 ECS 上，**未备案域名走 80/443 会被拦截**。
#  换非标端口可以先跑通，等域名备案下来再改回 443（本脚本支持两种模式）。
#
#  两种证书模式：
#    selfsigned（默认）—— 自签证书，浏览器会提示「不安全」，点继续即可用；
#                         不需要域名、不需要备案，适合答辩前的自测。
#    letsencrypt       —— 用 DNS-01 校验签发（不需要 80 端口、不受备案拦截），
#                         需要域名 + 在 DNS 商处添加一条 TXT 记录（脚本会提示）。
#
#  用法：
#    sudo bash setup-https.sh                       # 自签 + 8443
#    sudo bash setup-https.sh --domain exam.example.com   # LE + 8443
#    sudo bash setup-https.sh --port 443            # 已备案域名可改回 443
# ============================================================================
set -uo pipefail

PORT=8443
DOMAIN=""
EMAIL=""
MODE="selfsigned"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain) DOMAIN="$2"; MODE="letsencrypt"; shift 2 ;;
    --email) EMAIL="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --self-signed) MODE="selfsigned"; shift ;;
    *) echo "未知参数：$1"; exit 1 ;;
  esac
done

log()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

[[ ${EUID:-$(id -u)} -eq 0 ]] || die "请用 root 运行"

CERT_DIR=/etc/nginx/certs
mkdir -p "$CERT_DIR"

if [[ $MODE == selfsigned ]]; then
  log "生成自签证书（10 年有效，CN=exam-system）"
  # SAN 里带上公网 IP，浏览器至少不会因为「名字不匹配」而多一层抱怨
  PUBIP=$(curl -fsS --max-time 5 http://100.100.100.200/latest/meta-data/eipv4 2>/dev/null || echo "")
  SAN="DNS:localhost,IP:127.0.0.1"
  [[ -n $PUBIP ]] && SAN="$SAN,IP:$PUBIP"
  openssl req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
    -keyout "$CERT_DIR/exam.key" -out "$CERT_DIR/exam.crt" \
    -subj "/C=CN/O=exam-system/CN=exam-system" \
    -addext "subjectAltName=$SAN" >/dev/null 2>&1 || die "openssl 生成证书失败"
  chmod 600 "$CERT_DIR/exam.key"
  log "已生成：$CERT_DIR/exam.crt（SAN: $SAN）"
else
  [[ -n $DOMAIN ]] || die "letsencrypt 模式需要 --domain"
  log "用 certbot 的 DNS-01 校验为 $DOMAIN 签发证书"
  if ! command -v certbot >/dev/null 2>&1; then
    if command -v apt-get >/dev/null 2>&1; then
      apt-get update -qq && apt-get install -y -qq certbot
    else
      dnf install -y -q certbot
    fi
  fi
  # DNS-01：不碰 80 端口，因此不受「未备案域名 80/443 被拦」影响。
  # 手动模式会停下来提示你加 TXT 记录（需要域名 DNS 管理权限）。
  certbot certonly --manual --preferred-challenges dns \
    --agree-tos --no-eff-email ${EMAIL:+-m "$EMAIL"} -d "$DOMAIN" || die "certbot 签发失败"
  ln -sf "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" "$CERT_DIR/exam.crt"
  ln -sf "/etc/letsencrypt/live/$DOMAIN/privkey.pem" "$CERT_DIR/exam.key"
  log "证书已链接到 $CERT_DIR/（证书续期后 nginx 重新加载即可）"
fi

log "写 nginx 站点（监听 $PORT，反代到 127.0.0.1:8080）"
SITE=/etc/nginx/conf.d/exam-https.conf
cat > "$SITE" <<EOF
# 由 deploy/server/setup-https.sh 生成 —— HTTPS 站点（人脸识别需要安全上下文）
server {
    listen $PORT ssl;
    http2 on;
    server_name ${DOMAIN:-_};

    ssl_certificate     $CERT_DIR/exam.crt;
    ssl_certificate_key $CERT_DIR/exam.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_session_cache   shared:SSL:4m;
    ssl_session_timeout 1h;

    charset utf-8;
    access_log /var/log/nginx/exam_https_access.log;
    error_log  /var/log/nginx/exam_https_error.log;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options SAMEORIGIN always;
    add_header Referrer-Policy same-origin always;

    # 摄像头（getUserMedia）在本域内使用，不需要额外权限头；
    # 但把 Permissions-Policy 显式写成只允许本域，避免以后被默认策略坑到
    add_header Permissions-Policy "camera=(self), microphone=(), geolocation=()" always;

    location /api/ {
        proxy_pass http://exam_backend;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    location /ws/ {
        proxy_pass http://exam_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_read_timeout 3600s;
    }

    location /health {
        proxy_pass http://exam_backend/actuator/health;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Port $server_port;
        access_log off;
    }

    location / {
        proxy_pass http://exam_backend;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    }
}
EOF

nginx -t || die "nginx 配置校验失败（先确认 deploy/nginx/exam-system.conf 里的 upstream exam_backend 已存在）"
systemctl reload nginx || systemctl restart nginx
log "HTTPS 已就绪：https://${DOMAIN:-<公网IP>}:$PORT/"

# 防火墙（宿主侧）
if systemctl is-active --quiet firewalld 2>/dev/null; then
  firewall-cmd --permanent --add-port=$PORT/tcp >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
  log "firewalld 已放行 $PORT/tcp"
fi

cat <<TIP

  别忘了在【阿里云安全组】放行 $PORT/tcp（来源建议写你自己的出口 IP）。
  手机上的自测步骤：
    1) 用 https://${DOMAIN:-<公网IP>}:$PORT/ 打开（自签证书会先弹一次「不安全」，点继续）
    2) 登录 → 右上角用户菜单 →「人脸录入」→ 开始采集 → 按提示眨一下眼
    3) 若采集失败，先打开 /face-test.html 看是权限、摄像头还是模型的问题
TIP