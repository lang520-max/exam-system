#!/usr/bin/env bash
# ============================================================================
#  exam-system 云服务器初始化 —— 面向 2 vCPU / 2 GiB 内存的裸机部署
#
#  做什么（全部幂等，可重复执行）：
#    1. 识别发行版（apt / dnf / yum）与内存实况
#    2. 内存不足 1 GiB 时补一个 2 GiB swap 文件（2 GiB 机器的救命垫）
#    3. 只装运行物：JDK 17 + MySQL 8 + Redis + Nginx + python3
#       （刻意不装 Maven / Docker —— 2 核机器上构建会 OOM，jar 在本机打好再上传）
#    4. MySQL / Redis 按 2 GiB 调参，并且都只监听 127.0.0.1
#    5. 生成密钥写入 /etc/exam-system/exam.env（已存在则复用，不会重新生成）
#    6. 建库建应用账号、写 nginx 站点、装防火墙规则
#
#  ★ 关掉 RabbitMQ 是有意为之：2 GiB 跑不动「MySQL+RabbitMQ+Redis+JVM+Nginx」全家桶。
#    app.rabbitmq.enabled=false 时交卷走 GradingEngine 同步判分，业务语义一致
#    （见 docs/会话交接-项目当前状态.md §3.4 的中间件开关矩阵）。
#
#  用法：sudo bash bootstrap.sh
# ============================================================================
set -euo pipefail

APP_USER=exam
APP_DIR=/opt/exam-system
CONF_DIR=/etc/exam-system
ENV_FILE="$CONF_DIR/exam.env"
JAVA_ENV="$CONF_DIR/java.env"
LOG_DIR=/var/log/exam-system

log()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

[[ ${EUID:-$(id -u)} -eq 0 ]] || die "请用 root 运行：sudo bash bootstrap.sh"

# ---------------------------------------------------------------- 0. 环境识别
if [[ -r /etc/os-release ]]; then . /etc/os-release; fi
log "系统 ${PRETTY_NAME:-unknown} / 内核 $(uname -r) / 架构 $(uname -m)"

PKG=""
if   command -v apt-get >/dev/null 2>&1; then PKG=apt
elif command -v dnf     >/dev/null 2>&1; then PKG=dnf
elif command -v yum     >/dev/null 2>&1; then PKG=yum
else die "找不到 apt / dnf / yum，无法自动安装依赖"
fi
log "包管理器：$PKG"

# ---------------------------------------------------------------- 1. swap 安全垫
MEM_MB=$(awk '/^MemTotal:/{printf "%d", $2/1024}' /proc/meminfo)
SWAP_MB=$(awk '/^SwapTotal:/{printf "%d", $2/1024}' /proc/meminfo)
log "内存 ${MEM_MB} MiB / swap ${SWAP_MB} MiB"
(( MEM_MB >= 900 )) || die "内存只有 ${MEM_MB} MiB，MySQL + JVM 至少要 1.2 GiB，请升配"
# 云厂商标称「2 GiB」实际只给到约 1.6 GiB（Hypervisor 预留），实测阿里云 ECS 是 1670 MiB。
# 所以 JVM 堆按可用内存配成 384m，而不是按标称值配 512m。
if (( MEM_MB < 1950 )); then
  log "可用内存 ${MEM_MB} MiB（标称 2 GiB 的机型实际就给这么多），JVM 堆按此定为 384m"
fi
if (( SWAP_MB < 1024 )); then
  log "创建 2 GiB swap（不是为了让应用常驻换页，而是给交卷高峰留缓冲，避免 OOM Killer 直接杀 MySQL）"
  if [[ ! -f /swapfile ]]; then
    fallocate -l 2G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
  fi
  swapon /swapfile 2>/dev/null || true
  grep -qE '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  printf 'vm.swappiness=10\nvm.vfs_cache_pressure=50\n' > /etc/sysctl.d/99-exam-system.conf
  sysctl -q -p /etc/sysctl.d/99-exam-system.conf
else
  log "系统已有 swap（${SWAP_MB} MiB），跳过创建"
fi

# ---------------------------------------------------------------- 2. 安装运行物
log "安装 JDK 17 / MySQL / Redis / Nginx / python3"
# 逐个装、逐个报：不同发行版的包名有差异（Redis 有 redis / redis6 / redis7 三种叫法），
# 一次性 apt-get install 全部包时，一个包名不存在会导致【整批】不装，且报错很难读。
install_one() {
  local p
  for p in "$@"; do
    if [[ $PKG == apt ]]; then
      DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$p" >/dev/null 2>&1 && { printf '%s\n' "$p"; return 0; }
    else
      $PKG install -y -q "$p" >/dev/null 2>&1 && { printf '%s\n' "$p"; return 0; }
    fi
  done
  return 1
}
if [[ $PKG == apt ]]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  JAVA_PKG=$(install_one openjdk-17-jre-headless) || warn "JDK 17 包装不上"
  MYSQL_PKG=$(install_one mysql-server)            || warn "mysql-server 装不上"
  REDIS_PKG=$(install_one redis-server)            || warn "redis-server 装不上"
  NGINX_PKG=$(install_one nginx)                   || warn "nginx 装不上"
else
  JAVA_PKG=$(install_one java-17-openjdk-headless) || warn "JDK 17 包装不上"
  MYSQL_PKG=$(install_one mysql-server mysql-server-8.0 mysql-community-server) || warn "mysql-server 装不上（仓库里可能没有 MySQL）"
  REDIS_PKG=$(install_one redis redis6 redis7 valkey) || warn "redis 装不上（仓库里可能没有 Redis）"
  NGINX_PKG=$(install_one nginx)                   || warn "nginx 装不上"
fi
install_one curl ca-certificates tzdata openssl python3 >/dev/null || true
log "包：JAVA=${JAVA_PKG:-缺} MYSQL=${MYSQL_PKG:-缺} REDIS=${REDIS_PKG:-缺} NGINX=${NGINX_PKG:-缺}"
[[ -n ${MYSQL_PKG:-} ]] || die "MySQL 装不上 —— 这个发行版的仓库里没有可用的 mysql-server，需要换镜像或手工装"
[[ -n ${REDIS_PKG:-} ]] || die "Redis 装不上 —— 可以用 APP_REDIS_ENABLED=false 降级（见文档），但先确认包名"
[[ -n ${NGINX_PKG:-} ]] || die "Nginx 装不上"
timedatectl set-timezone Asia/Shanghai 2>/dev/null || ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
# 把实际版本打出来：MySQL 8.0 与 8.4 在调参项上有差异，出问题时这是第一条线索
command -v mysqld >/dev/null 2>&1 && log "mysqld: $(mysqld --version 2>/dev/null | head -1)"
command -v redis-server >/dev/null 2>&1 && log "redis:  $(redis-server --version 2>/dev/null | head -1)"
command -v nginx >/dev/null 2>&1 && log "nginx:  $(nginx -v 2>&1 | head -1)"

# MariaDB 兜底检查：本项目的 Flyway 脚本是 MySQL 8 方言，没在 MariaDB 上验证过
MARIADB=0
if command -v mysqld >/dev/null 2>&1 && mysqld --version 2>/dev/null | grep -qi mariadb; then
  [[ ${ALLOW_MARIADB:-0} == 1 ]] || die "装到的是 MariaDB 而不是 MySQL 8（Flyway 脚本未在它上面验证）。确认要用请 ALLOW_MARIADB=1 bash bootstrap.sh"
  MARIADB=1
  warn "检测到 MariaDB：跳过 mysqlx/skip-log-bin 等 MySQL 专有项"
fi

find_java17() {
  local c
  for c in /usr/lib/jvm/java-17-openjdk*/bin/java \
           /usr/lib/jvm/jre-17-openjdk*/bin/java \
           /usr/lib/jvm/java-1.17.0-openjdk*/bin/java \
           /usr/lib/jvm/temurin-17*/bin/java \
           /usr/bin/java; do
    [[ -x $c ]] || continue
    if "$c" -version 2>&1 | grep -q 'version "17'; then printf '%s\n' "$c"; return 0; fi
  done
  return 1
}
JAVA_BIN=$(find_java17) || die "没找到 JDK 17（openjdk-17-jre-headless 装了却找不到？请手工确认）"
log "JDK：$JAVA_BIN（$("$JAVA_BIN" -version 2>&1 | head -1)）"

# 装完包必须先 daemon-reload：新装的单元文件在 systemd 眼里可能还没出现，
# 紧接着 list-unit-files 会误判成「这个服务不存在」——
# 在 Alibaba Cloud Linux 4 上实测踩到（MySQL 装好了却报「找不到 MySQL 服务单元」）。
systemctl daemon-reload 2>/dev/null || true

svc_unit() {
  local want f
  for want in "$@"; do
    # 1) 标准查询
    if systemctl list-unit-files --type=service 2>/dev/null | awk '{print $1}' | grep -qx "${want}.service"; then
      printf '%s\n' "$want"; return 0
    fi
    # 2) 直接看单元文件（list-unit-files 看不到新装单元时的退路）
    for f in /usr/lib/systemd/system /etc/systemd/system /lib/systemd/system; do
      [[ -f "$f/${want}.service" ]] && { printf '%s\n' "$want"; return 0; }
    done
    # 3) systemctl cat 能读到也算
    systemctl cat "${want}.service" >/dev/null 2>&1 && { printf '%s\n' "$want"; return 0; }
  done
  return 1
}

MYSQL_UNIT=$(svc_unit mysql mysqld mariadb) || MYSQL_UNIT=""
if [[ -z $MYSQL_UNIT ]]; then
  warn "没找到 MySQL 的 systemd 单元，先把现场打出来（下次直接照这个判断）"
  printf '  /usr/lib/systemd/system 下的 mysql/maria 单元：\n'
  ls -1 /usr/lib/systemd/system 2>/dev/null | grep -iE 'mysql|maria' | sed 's/^/    /' || printf '    （一个都没有）\n'
  printf '  mysql-server 包里的 unit 文件：\n'
  rpm -ql mysql-server 2>/dev/null | grep -iE 'systemd|\.service' | sed 's/^/    /' || printf '    （包里没有 unit 文件）\n'
  MYSQLD_BIN=""
  for b in /usr/sbin/mysqld /usr/libexec/mysqld; do [[ -x $b ]] && { MYSQLD_BIN=$b; break; }; done
  printf '  mysqld 二进制：%s\n' "${MYSQLD_BIN:-找不到}"
  printf '  数据目录：%s\n' "$(ls -ld /var/lib/mysql 2>/dev/null || echo '不存在')"

  if [[ -n $MYSQLD_BIN && -d /var/lib/mysql ]]; then
    # 数据目录是空的（包里 %post 没初始化）就先初始化，否则 mysqld 起不来
    if [[ ! -d /var/lib/mysql/mysql ]]; then
      warn "数据目录未初始化，执行 $MYSQLD_BIN --initialize-insecure（root 空密码，随后脚本会设随机密码）"
      "$MYSQLD_BIN" --initialize-insecure --user=mysql || die "MySQL 数据目录初始化失败"
    fi
    warn "自建兜底单元 /etc/systemd/system/mysqld.service（ExecStart=$MYSQLD_BIN）"
    cat > /etc/systemd/system/mysqld.service <<UNIT
[Unit]
Description=MySQL Server (exam-system fallback unit)
Documentation=man:mysqld(8)
After=network.target

[Service]
Type=simple
User=mysql
Group=mysql
ExecStart=$MYSQLD_BIN --user=mysql
Restart=on-failure
RestartSec=5
LimitNOFILE=10000

[Install]
WantedBy=multi-user.target
UNIT
    systemctl daemon-reload
    MYSQL_UNIT=mysqld
    MYSQL_FALLBACK=1
  else
    die "MySQL 装了却没有 systemd 单元，也没有可用的 mysqld/数据目录 —— 需要人工处理（把上面的现场信息发我）"
  fi
fi
REDIS_UNIT=$(svc_unit redis-server redis redis6 redis7 valkey) || REDIS_UNIT=""
if [[ -z $REDIS_UNIT ]]; then
  printf '  /usr/lib/systemd/system 下的 redis 单元：\n'
  ls -1 /usr/lib/systemd/system 2>/dev/null | grep -iE 'redis|valkey' | sed 's/^/    /' || printf '    （一个都没有）\n'
  die "找不到 Redis 服务单元（上面是现场信息；也可用 APP_REDIS_ENABLED=false 降级）"
fi
NGINX_UNIT=$(svc_unit nginx) || die "找不到 nginx 服务单元"
log "服务单元：MySQL=$MYSQL_UNIT Redis=$REDIS_UNIT Nginx=$NGINX_UNIT"
systemctl enable --now "$MYSQL_UNIT" >/dev/null
systemctl enable --now "$REDIS_UNIT" >/dev/null

# ---------------------------------------------------------------- 2.5 应用用户
# 必须早于第 3 节：第 3 节要把 java.env 的属组设成 exam（让 start.sh 以 exam 身份
# 能读到它），用户还不存在时 chown 会直接失败。
id -u "$APP_USER" >/dev/null 2>&1 || useradd -r -M -d "$APP_DIR" -s /usr/sbin/nologin "$APP_USER"
log "应用用户：$APP_USER（uid=$(id -u "$APP_USER")）"

# ---------------------------------------------------------------- 3. 密钥与环境文件
mkdir -p "$CONF_DIR" "$APP_DIR" "$LOG_DIR"
# 750 + 属组 exam：exam 需要【穿过】这个目录去读 java.env。
# 含密钥的 exam.env 仍是 0600 root:root —— 它由 systemd（PID1，root）读取，
# 应用进程只通过环境变量拿到值，不需要读文件本身。
chown root:"$APP_USER" "$CONF_DIR"
chmod 750 "$CONF_DIR"
if [[ -f $ENV_FILE ]]; then
  log "复用已有 $ENV_FILE（不会重新生成密钥）"
  DB_PASSWORD=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
  REDIS_PASSWORD=$(grep -E '^REDIS_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
  JWT_SECRET=$(grep -E '^JWT_SECRET=' "$ENV_FILE" | cut -d= -f2-)
else
  log "生成新密钥（DB / Redis / JWT）"
  # 密码形状刻意凑齐「大写 + 小写 + 数字 + 特殊字符」：
  # RHEL 系（Alibaba Cloud Linux / CentOS）的 mysql-server 有可能启用
  # validate_password(MEDIUM) 插件，纯 hex 密码会被它直接拒绝 —— 生成时就绕开这个坑。
  DB_PASSWORD="$(openssl rand -hex 12)Aa1!"
  REDIS_PASSWORD="$(openssl rand -hex 12)Aa1!"
  JWT_SECRET=$(openssl rand -hex 32)   # 64 个 hex 字符 = 32 字节 = HS256 要求的 256 bit
fi
[[ -n ${DB_PASSWORD:-} && -n ${REDIS_PASSWORD:-} && -n ${JWT_SECRET:-} ]] || die "密钥读取/生成失败"

cat > "$JAVA_ENV" <<EOF
# 由 deploy/server/bootstrap.sh 生成 —— JVM 参数与 java 路径（2 GiB 内存适配）
# 改完执行：systemctl restart exam-system
# 注意：这份文件由 /opt/exam-system/start.sh 读取（systemd 不再直接读它，原因见 start.sh 注释）
JAVA_BIN=$JAVA_BIN
JAVA_OPTS=-Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -XX:MaxDirectMemorySize=48m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai
EOF
# java.env 里【没有密钥】（只有 java 路径与 JVM 参数），而 start.sh 是以 exam 身份
# 运行的、必须能读到它。原先设成 600 root:root 会导致 start.sh 读不到 → JAVA_BIN 为空
# → systemd 侧表现为反复 203/EXEC（2026-09-16 真机踩到，靠启动前自检抓到）。
chown root:"$APP_USER" "$JAVA_ENV"
chmod 640 "$JAVA_ENV"

# ---------------------------------------------------------------- 3.5 启动包装脚本
# ★ 为什么要多这一层：systemd【不会展开 ExecStart 里「命令位置」上的变量】。
#   原先写 ExecStart=\$JAVA_BIN \$JAVA_OPTS -jar app.jar，
#   实测结果：systemd 反复报 `Main process exited, code=exited, status=203/EXEC`
#   且日志里一行 Java 输出都没有（JVM 从未启动）——
#   因为命令位置上的变量没被替换成路径，execve() 直接 ENOENT。
#   （systemd 社区为此提过 feature request：systemd#23235）
#   所以：ExecStart 必须是字面路径，java 路径与参数在这里就落成实际值。
cat > "$APP_DIR/start.sh" <<EOF
#!/bin/bash
# 由 deploy/server/bootstrap.sh 生成 —— 应用启动包装脚本
#
# 存在的原因：systemd 不展开 ExecStart 命令位置上的变量，ExecStart=\\\$JAVA_BIN ...
# 会以 status=203/EXEC 失败且没有任何 Java 日志（真机上踩过）。因此 unit 里
# ExecStart 指向本脚本（字面路径），java 路径与 JVM 参数从 java.env 读出来。
#
# 应用自身的环境变量（DB/Redis/JWT 等）仍由 systemd 的
# EnvironmentFile=/etc/exam-system/exam.env 注入，本脚本原样继承。
set -uo pipefail

JAVA_ENV=/etc/exam-system/java.env
JAVA_BIN=\$(sed -n 's/^JAVA_BIN=//p' "\$JAVA_ENV" | head -1)
JAVA_OPTS=\$(sed -n 's/^JAVA_OPTS=//p' "\$JAVA_ENV" | head -1)

if [[ ! -x \$JAVA_BIN ]]; then
  echo "start.sh: JAVA_BIN 无效：[\$JAVA_BIN]（检查 \$JAVA_ENV）" >&2
  exit 78
fi

cd /opt/exam-system
# \$JAVA_OPTS 刻意不加引号：要按空格拆成多个参数
exec "\$JAVA_BIN" \$JAVA_OPTS -jar /opt/exam-system/app.jar --spring.profiles.active=prod
EOF
chmod 755 "$APP_DIR/start.sh"
log "启动包装脚本：$APP_DIR/start.sh（java=$JAVA_BIN）"

cat > "$ENV_FILE" <<EOF
# ============================================================================
#  exam-system 运行时环境（由 bootstrap.sh 生成，0600 root:root，切勿入 git）
#
#  RABBITMQ_USER / RABBITMQ_PASS：本机没装 RabbitMQ，填占位值即可（留着是为了将来
#  真接上 RabbitMQ 时不用再补）。实测（本机 jar + prod profile，见部署文档 §5）：
#    - APP_RABBITMQ_ENABLED=false 时，这两个变量缺失【也不影响启动】，
#      且日志里 0 次连接尝试 —— 关掉是真关掉；
#    - 反过来（开关打开但没给账号）应用同样能启动，只是每几秒刷一次
#      UnknownHostException 堆栈：占位符未解析不会让启动失败，别指望它当保险。
# ============================================================================
SPRING_PROFILES_ACTIVE=prod
TZ=Asia/Shanghai

# ---- MySQL（只监听 127.0.0.1）----
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USERNAME=exam
DB_PASSWORD=$DB_PASSWORD
DB_SSL=false
DB_ALLOW_PUBLIC_KEY_RETRIEVAL=true

# ---- Redis（幂等 + 登录限流 + refresh token 黑名单；只监听 127.0.0.1）----
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=$REDIS_PASSWORD
APP_REDIS_ENABLED=true

# ---- RabbitMQ：本机没装，显式关闭 ----
# 关掉后：不注册 @RabbitListener、交卷走同步判分 GradingEngine，业务语义一致。
# 省下 Erlang VM 的 200-300 MiB —— 这正是 2 GiB 机器能稳住的关键。
APP_RABBITMQ_ENABLED=false
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_USER=unused-rabbitmq-disabled
RABBITMQ_PASS=unused-rabbitmq-disabled

# ---- JWT（无默认值，缺失即启动失败，这是刻意设计）----
JWT_SECRET=$JWT_SECRET

# ---- 主观题 AI 评分（按量付费，默认关）----
AI_ENABLED=false
AI_API_KEY=
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_MODEL=qwen-plus
EOF
chmod 600 "$ENV_FILE"
log "已写入 $ENV_FILE 与 $JAVA_ENV"

# ---------------------------------------------------------------- 4. MySQL 调参
# 调参块先攒成变量：写到哪里要看发行版的配置包含关系（下面判断）
MYSQL_CNF_BLOCK="$(
  echo '[mysqld]'
  echo 'bind-address = 127.0.0.1'
  echo 'skip-name-resolve'
  echo 'character-set-server = utf8mb4'
  echo 'collation-server = utf8mb4_unicode_ci'
  echo "default-time-zone = '+08:00'"
  echo 'innodb_buffer_pool_size = 96M'
  echo 'innodb_buffer_pool_instances = 1'
  echo 'innodb_flush_log_at_trx_commit = 2'
  echo '# performance_schema 在 8.0 默认开着，光它就要 150-200 MiB；本项目不用它'
  echo 'performance_schema = OFF'
  echo 'max_connections = 100'
  echo 'table_open_cache = 400'
  echo 'tmp_table_size = 16M'
  echo 'max_heap_table_size = 16M'
  if [[ $MARIADB == 0 ]]; then
    echo '# 单机部署不需要 binlog 与 X 协议：省内存、少写放大、少一个监听端口'
    echo 'skip-log-bin'
    echo '# loose- 前缀：万一该插件不存在也不会让 mysqld 启动失败'
    echo 'loose-mysqlx = OFF'
  fi
)"

if [[ $PKG == apt ]]; then
  MYSQL_DROPIN=/etc/mysql/mysql.conf.d/zz-exam-system.cnf
  mkdir -p /etc/mysql/mysql.conf.d
  printf '%s\n' "$MYSQL_CNF_BLOCK" > "$MYSQL_DROPIN"
else
  MYSQL_DROPIN=/etc/my.cnf.d/zz-exam-system.cnf
  mkdir -p /etc/my.cnf.d
  if grep -qE '^[[:space:]]*!include(dir)?[[:space:]]+/etc/my\.cnf\.d' /etc/my.cnf 2>/dev/null; then
    printf '%s\n' "$MYSQL_CNF_BLOCK" > "$MYSQL_DROPIN"
  else
    # 发行版自编译的 MySQL 其 /etc/my.cnf 未必 include /etc/my.cnf.d
    # （Alibaba Cloud Linux 4 的 mysql-server 就是自编译包）。
    # 那种情况下把调参块【追加】到 /etc/my.cnf 末尾 —— MySQL 对重复项取最后一个值，
    # 所以追加在最后是有效的；注意绝不能覆盖写，那会毁掉包自带的 datadir/socket 配置。
    MYSQL_DROPIN=/etc/my.cnf
    if ! grep -q 'exam-system 2 GiB 调参' /etc/my.cnf 2>/dev/null; then
      printf '\n# ==== exam-system 2 GiB 调参（由 bootstrap.sh 追加，可重复执行不会重复追加）====\n%s\n' \
        "$MYSQL_CNF_BLOCK" >> /etc/my.cnf
    fi
    warn "/etc/my.cnf 未 include /etc/my.cnf.d，调参块已直接追加到 /etc/my.cnf"
  fi
fi
chmod 644 "$MYSQL_DROPIN" 2>/dev/null || true
log "MySQL 调参写入 $MYSQL_DROPIN（buffer pool 96M + performance_schema=OFF）"
systemctl restart "$MYSQL_UNIT"

mysql_root_args=()
mysql_root() {
  [[ ${#mysql_root_args[@]} -gt 0 ]] && return 0
  # 1) socket 直连（Ubuntu/Debian 的 root 走 auth_socket）
  if mysql -uroot -e 'SELECT 1' >/dev/null 2>&1; then mysql_root_args=(-uroot); return 0; fi
  # 2) 上次 bootstrap 自己设过的 root 密码（重跑时用这条，否则会误判成「连不上」）
  local saved
  saved=$(grep -E '^MYSQL_ROOT_PASSWORD=' "$CONF_DIR/mysql-root.txt" 2>/dev/null | cut -d= -f2-)
  if [[ -n ${saved:-} ]] && mysql -uroot -p"$saved" -e 'SELECT 1' >/dev/null 2>&1; then
    mysql_root_args=(-uroot -p"$saved")
    return 0
  fi
  # 3) RHEL 系首次安装：root 初始随机密码写在错误日志里，且首次登录可能处于 expired 状态
  local lg pw
  for lg in /var/log/mysqld.log /var/log/mysql/error.log /var/log/mysql/mysqld.log; do
    [[ -r $lg ]] || continue
    pw=$(grep -oE 'temporary password is generated for .*root@localhost: .*' "$lg" 2>/dev/null | tail -1 | sed 's/.*: //')
    [[ -n ${pw:-} ]] || continue
    if mysql --connect-expired-password -uroot -p"$pw" -e 'SELECT 1' >/dev/null 2>&1; then
      mysql_root_args=(--connect-expired-password -uroot -p"$pw")
      return 0
    fi
  done
  return 1
}
mysql_root || die "连不上 MySQL 的 root（socket 认证不行、$CONF_DIR/mysql-root.txt 里的密码不对、错误日志里也没有可用的初始密码）"

# RHEL 系第一次用初始密码时会被要求改密：改成强随机并留档
if [[ ${mysql_root_args[0]} == --connect-expired-password ]]; then
  ROOT_PW="$(openssl rand -hex 12)Aa1!"
  mysql "${mysql_root_args[@]}" -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$ROOT_PW';" 2>/dev/null || true
  # 第一行写成 KEY=VALUE 形式：mysql_root() 重跑时要按机器可读的方式取回它
  printf 'MYSQL_ROOT_PASSWORD=%s\n# 上一行是 bootstrap.sh 生成的 MySQL root 密码，重跑脚本会复用它，请自行抄到密码管理器\n' "$ROOT_PW" > "$CONF_DIR/mysql-root.txt"
  chmod 600 "$CONF_DIR/mysql-root.txt"
  mysql_root_args=(-uroot -p"$ROOT_PW")
  warn "MySQL root 已设随机密码，记录在 $CONF_DIR/mysql-root.txt"
elif [[ ${MYSQL_FALLBACK:-0} == 1 ]]; then
  # 自建单元那条路是用 --initialize-insecure 初始化的：root 是【空密码】，
  # 必须补一个随机密码，否则本机任何用户都能用 socket 空密码连上 root。
  ROOT_PW="$(openssl rand -hex 12)Aa1!"
  mysql "${mysql_root_args[@]}" -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$ROOT_PW';" 2>/dev/null || true
  printf 'MYSQL_ROOT_PASSWORD=%s\n# 上一行是 bootstrap.sh 为兜底初始化生成的 root 密码，重跑脚本会复用它\n' "$ROOT_PW" > "$CONF_DIR/mysql-root.txt"
  chmod 600 "$CONF_DIR/mysql-root.txt"
  mysql_root_args=(-uroot -p"$ROOT_PW")
  warn "兜底初始化出来的 MySQL root 原本是空密码，已改为随机密码并记录在 $CONF_DIR/mysql-root.txt"
elif [[ -f $CONF_DIR/mysql-root.txt ]]; then
  :
else
  printf '# MySQL root 走 auth_socket（Ubuntu/Debian 默认），本机 sudo mysql 即可登录，无密码。\n' > "$CONF_DIR/mysql-root.txt"
  chmod 600 "$CONF_DIR/mysql-root.txt"
fi

# 实测参数有没有真的生效（配置文件写对 ≠ 被加载：发行版的 include 路径可能不同）
MYSQL_BP=$(mysql "${mysql_root_args[@]}" -N -B -e 'SELECT @@innodb_buffer_pool_size' 2>/dev/null || echo '?')
MYSQL_PS=$(mysql "${mysql_root_args[@]}" -N -B -e 'SELECT @@performance_schema' 2>/dev/null || echo '?')
log "MySQL 生效参数：innodb_buffer_pool_size=$MYSQL_BP  performance_schema=$MYSQL_PS"
[[ $MYSQL_PS == 0 ]] || warn "performance_schema 仍为 $MYSQL_PS —— $MYSQL_DROPIN 可能没被 include 进配置，检查 /etc/my.cnf 或 /etc/mysql/my.cnf"

log "建库 exam_system + 应用账号 exam（只授权到本库）"
mysql "${mysql_root_args[@]}" <<SQL
CREATE DATABASE IF NOT EXISTS exam_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'exam'@'localhost'  IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS 'exam'@'127.0.0.1'  IDENTIFIED BY '$DB_PASSWORD';
ALTER USER 'exam'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER 'exam'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON exam_system.* TO 'exam'@'localhost';
GRANT ALL PRIVILEGES ON exam_system.* TO 'exam'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

# ---------------------------------------------------------------- 5. Redis 调参
REDIS_CONF=""
for c in /etc/redis/redis.conf /etc/redis.conf; do [[ -f $c ]] && { REDIS_CONF=$c; break; }; done
[[ -n $REDIS_CONF ]] || die "找不到 redis.conf"
REDIS_RUN_USER=$(id -un redis 2>/dev/null || echo root)
REDIS_OVERRIDE="$(dirname "$REDIS_CONF")/exam-override.conf"
# `bind 127.0.0.1 -::1` 里的 `-` 前缀（表示「该地址可选」）要求 Redis >= 6.0；
# Alibaba Cloud Linux 3 等发行版自带的老 redis 会因此**拒绝启动**，所以按版本给 bind 行。
REDIS_VER=$(redis-server --version 2>/dev/null | grep -oE 'v=[0-9]+' | head -1 | cut -d= -f2)
REDIS_BIND="bind 127.0.0.1"
if [[ ${REDIS_VER:-0} -ge 6 ]]; then REDIS_BIND="bind 127.0.0.1 -::1"; fi
log "Redis 版本大号：${REDIS_VER:-未知} → $REDIS_BIND"
cat > "$REDIS_OVERRIDE" <<EOF
# 由 deploy/server/bootstrap.sh 生成 —— 2 GiB 内存适配
# 只监听本机；本项目的 Redis 只放「幂等键 / 登录计数 / refresh token 黑名单」这类
# 短生命周期数据，所以关掉持久化、内存给 96 MiB 上限。
$REDIS_BIND
protected-mode yes
port 6379
requirepass $REDIS_PASSWORD
maxmemory 96mb
maxmemory-policy noeviction
appendonly no
save ""
EOF
chown root:"$REDIS_RUN_USER" "$REDIS_OVERRIDE" 2>/dev/null || true
chmod 640 "$REDIS_OVERRIDE"
grep -qF "include $REDIS_OVERRIDE" "$REDIS_CONF" || printf '\n# exam-system（2 GiB 调参，见 deploy/server/bootstrap.sh）\ninclude %s\n' "$REDIS_OVERRIDE" >> "$REDIS_CONF"
systemctl restart "$REDIS_UNIT"
sleep 1
if redis-cli -h 127.0.0.1 -a "$REDIS_PASSWORD" --no-auth-warning ping 2>/dev/null | grep -q PONG; then
  log "Redis 就绪（requirepass 生效，仅 127.0.0.1）"
else
  die "Redis 起不来或密码不对，检查 $REDIS_OVERRIDE 与 journalctl -u $REDIS_UNIT"
fi

# ---------------------------------------------------------------- 6. Nginx
log "配置 Nginx（覆写主配置，避开发行版自带的 default_server 抢站点）"
[[ -f /etc/nginx/nginx.conf && ! -f /etc/nginx/nginx.conf.distro-default ]] && \
  cp -p /etc/nginx/nginx.conf /etc/nginx/nginx.conf.distro-default
NGINX_USER=$(id -un www-data 2>/dev/null || id -un nginx 2>/dev/null || echo nobody)
cat > /etc/nginx/nginx.conf <<EOF
# 由 deploy/server/bootstrap.sh 生成（原发行版配置备份为 nginx.conf.distro-default）
user  $NGINX_USER;
worker_processes  auto;
worker_rlimit_nofile 8192;
error_log  /var/log/nginx/error.log warn;
pid        /run/nginx.pid;

events {
    worker_connections  2048;
    multi_accept        on;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    charset       utf-8;
    sendfile      on;
    tcp_nopush    on;
    tcp_nodelay   on;
    keepalive_timeout  65;
    client_max_body_size 8m;
    server_tokens off;

    gzip              on;
    gzip_min_length   1k;
    gzip_comp_level   5;
    gzip_types        text/plain text/css application/javascript application/json image/svg+xml;

    log_format main '\$remote_addr - \$remote_user [\$time_local] "\$request" \$status \$body_bytes_sent "\$http_referer" "\$http_user_agent" rt=\$request_time';
    access_log /var/log/nginx/access.log main;

    include /etc/nginx/conf.d/*.conf;
}
EOF
mkdir -p /etc/nginx/conf.d
if [[ -f $APP_DIR/deploy/nginx/exam-system.conf ]]; then
  install -m 644 "$APP_DIR/deploy/nginx/exam-system.conf" /etc/nginx/conf.d/exam-system.conf
else
  warn "缺少 $APP_DIR/deploy/nginx/exam-system.conf —— 先把 deploy/ 目录传上去再跑本脚本"
fi
rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true

# ---- SELinux（Alibaba Cloud Linux / CentOS 系）----
# 本项目的 nginx 是纯反代到 127.0.0.1:8080，SELinux enforcing 时默认禁止 httpd 主动外连，
# 表现是「页面 502，但应用日志一点错都没有」—— 极难查，所以这里主动放行。
if command -v getenforce >/dev/null 2>&1; then
  SE=$(getenforce 2>/dev/null || echo Disabled)
  log "SELinux：$SE"
  if [[ $SE == Enforcing ]]; then
    if setsebool -P httpd_can_network_connect 1 2>/dev/null; then
      log "已放行 httpd_can_network_connect（否则 nginx 反代会 502）"
    else
      warn "setsebool 失败：nginx 反代可能 502。手工执行 setsebool -P httpd_can_network_connect 1（缺命令就先 dnf install -y policycoreutils-python-utils）"
    fi
  fi
fi

nginx -t || die "nginx 配置校验失败"
systemctl enable --now nginx >/dev/null
log "Nginx 已启动"

# ---------------------------------------------------------------- 7. 防火墙
if systemctl is-active --quiet firewalld 2>/dev/null; then
  firewall-cmd --permanent --add-service=http >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
  log "firewalld 已放行 http"
fi
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
  ufw allow 'Nginx HTTP' >/dev/null 2>&1 || ufw allow 80/tcp >/dev/null 2>&1 || true
  log "ufw 已放行 80"
fi

# ---------------------------------------------------------------- 8. 应用目录
mkdir -p "$APP_DIR" "$LOG_DIR"
chown "$APP_USER:$APP_USER" "$APP_DIR" "$LOG_DIR"
chmod 750 "$LOG_DIR"

log "初始化完成。下一步："
cat <<'TIP'
  1) 上传运行物：  jar → /opt/exam-system/app.jar，deploy/ → /opt/exam-system/deploy/，
                   sql/  → /opt/exam-system/sql/
  2) 装并启动应用：sudo bash /opt/exam-system/deploy/server/deploy-app.sh /tmp/exam-system.jar
  3) 灌演示数据：  sudo bash /opt/exam-system/deploy/server/import-demo-data.sh
  4) 冒烟 + 内存： sudo python3 /opt/exam-system/deploy/server/smoke.py
TIP