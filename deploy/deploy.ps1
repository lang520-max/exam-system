<#
.SYNOPSIS
  exam-system 一键部署到 2 核 2 GiB 云服务器（本机构建 → SSH 上传 → 远端安装 → 冒烟）

.DESCRIPTION
  为什么构建放本机：2 核 2 GiB 上跑 Maven 或 Docker build 很容易 OOM。
  服务器只跑运行物（jar + systemd + MySQL + Redis + Nginx），不装 Maven / Docker。

  注意：本文件带 UTF-8 BOM —— Windows PowerShell 5.1 读无 BOM 的 UTF-8 会按 GBK 解，
  中文字符串会变成乱码并导致语法错误。改这个文件请保留 BOM。

.EXAMPLE
  # 密码登录（密码放环境变量，不进命令行历史）
  $env:EXAM_SSH_PASSWORD = '你的登录密码'
  .\deploy\deploy.ps1 -Server 1.2.3.4 -User root

.EXAMPLE
  # 密钥登录（推荐）
  .\deploy\deploy.ps1 -Server 1.2.3.4 -User ubuntu -Key C:\keys\cloud.pem

.EXAMPLE
  # 只更新代码重新部署（服务器环境已就绪）
  .\deploy\deploy.ps1 -Server 1.2.3.4 -User root -SkipBootstrap -SkipData
#>
param(
    [Parameter(Mandatory = $true)][string]$Server,
    [int]$Port = 22,
    [string]$User = 'root',
    [string]$Key,
    [string]$PublicBase = '',
    [switch]$SkipBuild,
    [switch]$SkipBootstrap,
    [switch]$SkipData,
    [switch]$SkipSmoke
)

# 控制台是 GBK 时中文与 ✓ 会乱码：统一按 UTF-8 收发
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$env:PYTHONIOENCODING = 'utf-8'
$ErrorActionPreference = 'Continue'   # PS 5.1 里把原生命令 stderr 合并进管道会抛 NativeCommandError，故用退出码判定

$repo = Split-Path -Parent $PSScriptRoot
$helper = Join-Path $PSScriptRoot 'tools\ssh_win.py'
$py = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $py) { throw '找不到 python（需要它 + paramiko：python -m pip install paramiko）' }
if (-not (Test-Path $helper)) { throw "找不到 $helper" }

$useSudo = ($User -ne 'root')

function Invoke-Remote {
    param([string]$Command, [string]$LocalScript, [switch]$Quiet)
    $a = @($helper, '--host', $Server, '--port', "$Port", '--user', $User)
    if ($Key) { $a += @('--key', $Key) }
    if ($useSudo) { $a += '--sudo' }
    if ($LocalScript) { $a += @('--script', $LocalScript) }
    if ($Command) { $a += @('--cmd', $Command) }
    $out = & $py @a 2>&1
    if (-not $Quiet) { $out | ForEach-Object { Write-Host $_ } }
    if ($LASTEXITCODE -ne 0) { throw "远端执行失败（退出码 $LASTEXITCODE）：$LocalScript$Command" }
    return ($out -join "`n")
}

function Send-File {
    param([string]$Local, [string]$Remote)
    if (-not (Test-Path $Local)) { throw "本地文件不存在：$Local" }
    $a = @($helper, '--host', $Server, '--port', "$Port", '--user', $User)
    if ($Key) { $a += @('--key', $Key) }
    if ($useSudo) { $a += '--sudo' }
    $a += @('--put', "$Local`:$Remote")
    & $py @a 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "上传失败：$Local -> $Remote" }
}

Write-Host "`n=== 0/5 探测服务器 ===" -ForegroundColor Cyan
Invoke-Remote -Quiet -Command 'set -x; id -un; id -u; grep -E "^(PRETTY_NAME|ID)=" /etc/os-release; nproc; free -m | head -2; df -h / | tail -1; systemctl is-active firewalld 2>/dev/null || true' |
    ForEach-Object { Write-Host $_ }
Write-Host ("登录用户 {0}；sudo：{1}" -f $User, $(if ($useSudo) { '需要（非 root）' } else { '不需要（root）' }))

Write-Host "`n=== 1/5 本机构建（JDK 17） ===" -ForegroundColor Cyan
$jar = Join-Path $repo 'target\exam-system-2.0.0.jar'
if ($SkipBuild) {
    if (-not (Test-Path $jar)) { throw "-SkipBuild 但 $jar 不存在" }
    Write-Host "跳过构建，使用已有 $jar"
} else {
    Push-Location $repo
    try {
        $jv = (& mvn -v 2>&1 | Select-String 'Java version' | Select-Object -First 1)
        Write-Host $jv
        if ("$jv" -notmatch '17') { throw "Maven 用的不是 JDK 17（$jv）—— 检查 JAVA_HOME" }
        & mvn -B clean package
        if ($LASTEXITCODE -ne 0) { throw 'mvn package 失败' }
    } finally { Pop-Location }
}
if (-not (Test-Path $jar)) { throw "构建后仍找不到 $jar" }
Write-Host ("jar：{0}（{1:N1} MB）" -f $jar, ((Get-Item $jar).Length / 1MB))

Write-Host "`n=== 2/5 上传运行物 ===" -ForegroundColor Cyan
Invoke-Remote -Quiet -Command 'mkdir -p /opt/exam-system/deploy/server /opt/exam-system/deploy/nginx /opt/exam-system/deploy/systemd /opt/exam-system/deploy/tools /opt/exam-system/sql'
Send-File $jar '/tmp/exam-system-2.0.0.jar'
foreach ($f in 'bootstrap.sh', 'deploy-app.sh', 'import-demo-data.sh', 'smoke.py') {
    Send-File (Join-Path $PSScriptRoot "server\$f") "/opt/exam-system/deploy/server/$f"
}
Send-File (Join-Path $PSScriptRoot 'nginx\exam-system.conf') '/opt/exam-system/deploy/nginx/exam-system.conf'
Send-File (Join-Path $PSScriptRoot 'systemd\exam-system.service') '/opt/exam-system/deploy/systemd/exam-system.service'
Send-File (Join-Path $PSScriptRoot 'exam.env.example') '/opt/exam-system/deploy/exam.env.example'
if (Test-Path (Join-Path $PSScriptRoot 'README.md')) {
    Send-File (Join-Path $PSScriptRoot 'README.md') '/opt/exam-system/deploy/README.md'
}
Send-File (Join-Path $PSScriptRoot 'tools\ssh_win.py') '/opt/exam-system/deploy/tools/ssh_win.py'
foreach ($s in Get-ChildItem (Join-Path $repo 'sql') -Filter *.sql) {
    Send-File $s.FullName "/opt/exam-system/sql/$($s.Name)"
}
Invoke-Remote -Quiet -Command 'chmod +x /opt/exam-system/deploy/server/*.sh /opt/exam-system/deploy/server/smoke.py; ls -l /opt/exam-system/deploy/server/'

if (-not $SkipBootstrap) {
    Write-Host "`n=== 3/5 服务器初始化（swap / JDK17 / MySQL / Redis / Nginx + 2G 调参） ===" -ForegroundColor Cyan
    Invoke-Remote -LocalScript '/opt/exam-system/deploy/server/bootstrap.sh'
} else {
    Write-Host "`n=== 3/5 跳过服务器初始化（-SkipBootstrap） ===" -ForegroundColor Yellow
}

Write-Host "`n=== 4/5 安装并启动应用（systemd） ===" -ForegroundColor Cyan
Invoke-Remote -Command 'bash /opt/exam-system/deploy/server/deploy-app.sh /tmp/exam-system-2.0.0.jar'

if (-not $SkipData) {
    Write-Host "`n=== 5/5 导入演示数据（4 账号 + 150 题 + 6 卷） ===" -ForegroundColor Cyan
    Invoke-Remote -Command 'bash /opt/exam-system/deploy/server/import-demo-data.sh'
} else {
    Write-Host "`n=== 5/5 跳过演示数据（-SkipData） ===" -ForegroundColor Yellow
}

if (-not $SkipSmoke) {
    Write-Host "`n=== 冒烟测试 + 内存实况 ===" -ForegroundColor Cyan
    $base = if ($PublicBase) { $PublicBase } else { "http://$Server" }
    Invoke-Remote -Command "SMOKE_PUBLIC=$base python3 /opt/exam-system/deploy/server/smoke.py"
}

Write-Host "`n部署完成：http://$Server/  演示账号 admin / teacher1 / student01 / student02（口令 123456）" -ForegroundColor Green
Write-Host "运维：systemctl status exam-system ｜ journalctl -u exam-system -f ｜ free -m" -ForegroundColor Green