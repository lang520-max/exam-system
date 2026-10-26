# deploy/ —— 云服务器部署（2 核 2 GiB 裸机）

面向「2 vCPU / 2 GiB 内存」的云主机，**不使用 Docker**：服务器上不跑 Maven、不跑
Docker build（2 核机器上构建极易 OOM），只跑运行物。jar 在本机用 JDK 17 打好再上传。

## 交付形态

```
公网 ──▶ Nginx(宿主, :80) ──▶ Spring Boot(:8080, systemd) ──▶ MySQL(127.0.0.1:3306)
                                                          └─▶ Redis(127.0.0.1:6379)
```

- MySQL / Redis 都只监听 `127.0.0.1`，公网不可达（比改端口更彻底）。
- **RabbitMQ 刻意不装**：2 GiB 跑不动「MySQL+RabbitMQ+Redis+JVM+Nginx」全家桶。
  `APP_RABBITMQ_ENABLED=false` 时交卷走同步判分 `GradingEngine`，业务语义与异步一致
  （见 `docs/会话交接-项目当前状态.md` §3.4 中间件开关矩阵）。

## 文件

| 文件 | 作用 |
| --- | --- |
| `deploy.ps1` | **本地一键**：构建 → 上传 → 远端初始化 → 装应用 → 灌演示数据 → 冒烟 |
| `server/bootstrap.sh` | 服务器初始化（幂等）：swap / JDK17 / MySQL / Redis / Nginx / 密钥 / 建库 |
| `server/deploy-app.sh` | 装或升级 jar（带 `--rollback`）、写 systemd 单元、重启、等健康 |
| `server/import-demo-data.sh` | 导入 4 个演示账号 + 150 题 + 6 张卷（prod 档不会自动灌） |
| `server/smoke.py` | 冒烟：健康检查 / 鉴权 / 数据条数 / 学生全链路 + **内存实况** |
| `systemd/exam-system.service` | systemd 单元（含 `MemoryMax` 内存护栏） |
| `nginx/exam-system.conf` | 宿主 Nginx 站点（反代 8080 + WebSocket + `/health`） |
| `exam.env.example` | 运行时环境模板（真实文件在服务器 `/etc/exam-system/exam.env`，0600） |
| `tools/ssh_win.py` | Windows 侧 SSH 助手（paramiko）——Windows 自带 ssh 无法非交互传密码 |

密钥全部在服务器上随机生成（`openssl rand`），落在 `/etc/exam-system/exam.env`（0600 root），
**不进仓库**；重复执行 bootstrap 会复用它而不是重新生成。

## 用法

```powershell
# 首次部署（密码登录：密码放环境变量，不进命令行历史）
$env:EXAM_SSH_PASSWORD = '...'
.\deploy\deploy.ps1 -Server 1.2.3.4 -User root

# 或密钥登录
.\deploy\deploy.ps1 -Server 1.2.3.4 -User ubuntu -Key C:\keys\cloud.pem

# 后续只更新代码（环境已就绪）
.\deploy\deploy.ps1 -Server 1.2.3.4 -User root -SkipBootstrap -SkipData
```

服务器上常用命令：

```bash
systemctl status exam-system
journalctl -u exam-system -f
bash /opt/exam-system/deploy/server/deploy-app.sh --rollback   # 回滚上一版 jar
python3 /opt/exam-system/deploy/server/smoke.py                # 随时复测
free -m                                                        # 内存余量
```

完整说明、内存实测数据与运维注意事项见 `docs/部署-云服务器-2C2G.md`。