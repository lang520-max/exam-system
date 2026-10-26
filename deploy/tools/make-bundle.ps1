<#
.SYNOPSIS
  打包 Workbench 部署包（exam-deploy.tar.gz）：jar + deploy/ + sql/ + 根目录 install.sh

.DESCRIPTION
  为什么脚本化：手工 Copy-Item 组装时漏过一次根目录的 install.sh，
  服务端就报 `bash: /root/exam-deploy/install.sh: No such file or directory`
  —— 所以这里做完【强制自检】，少一个文件就直接失败，不产出残包。

  包里必须有的四样（缺一不可）：
    exam-deploy/install.sh                    ← 入口，必须在包根目录
    exam-deploy/exam-system-2.0.0.jar
    exam-deploy/deploy/server/bootstrap.sh
    exam-deploy/sql/data.sql + data-shengben.sql

.EXAMPLE
  .\deploy\tools\make-bundle.ps1                 # 打包并上传到临时托管，打印下载 URL
  .\deploy\tools\make-bundle.ps1 -NoUpload       # 只打包（走 Workbench 手动上传时用）
  .\deploy\tools\make-bundle.ps1 -SkipBuild      # 不重新 mvn package，用现有 target jar
#>
param(
    [string]$OutDir = "$env:TEMP\exam-bundle",
    [switch]$NoUpload,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # deploy/tools → deploy → 仓库根
$jar = Join-Path $repo 'target\exam-system-2.0.0.jar'
$stage = Join-Path $OutDir 'exam-deploy'
$tgz = Join-Path $OutDir 'exam-deploy.tar.gz'

if (-not $SkipBuild) {
    Write-Host '=== 1/4 构建（JDK 17，含 262 个测试） ===' -ForegroundColor Cyan
    Push-Location $repo
    try {
        $jv = (& mvn -v 2>&1 | Select-String 'Java version' | Select-Object -First 1)
        if ("$jv" -notmatch '17') { throw "Maven 用的不是 JDK 17（$jv）" }
        & mvn -B clean package
        if ($LASTEXITCODE -ne 0) { throw 'mvn package 失败' }
    } finally { Pop-Location }
}

Write-Host '=== 2/4 组装 ===' -ForegroundColor Cyan
if (-not (Test-Path $jar)) { throw "找不到 $jar（先构建或用 -SkipBuild 之前先构建一次）" }
Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item $jar $stage -Force
Copy-Item (Join-Path $repo 'deploy') $stage -Recurse -Force
Copy-Item (Join-Path $repo 'sql') $stage -Recurse -Force
Get-ChildItem $stage -Recurse -Directory -Filter '__pycache__' | Remove-Item -Recurse -Force
# ★ 关键一步：入口脚本必须落在包【根目录】（漏过这一次，记在注释里）
Copy-Item (Join-Path $repo 'deploy\workbench\install.sh') (Join-Path $stage 'install.sh') -Force

$required = @(
    'install.sh',
    'exam-system-2.0.0.jar',
    'deploy\server\bootstrap.sh',
    'deploy\server\deploy-app.sh',
    'deploy\server\import-demo-data.sh',
    'deploy\server\smoke.py',
    'deploy\nginx\exam-system.conf',
    'deploy\systemd\exam-system.service',
    'sql\data.sql',
    'sql\data-shengben.sql'
)
$missing = $required | Where-Object { -not (Test-Path (Join-Path $stage $_)) }
if ($missing) { throw "包内缺文件，拒绝出包：$($missing -join ', ')" }
Write-Host ("  自检通过：{0} 个必需文件齐全，共 {1} 个文件" -f $required.Count, (Get-ChildItem $stage -Recurse -File).Count)

Write-Host '=== 3/4 打包 ===' -ForegroundColor Cyan
Remove-Item $tgz -ErrorAction SilentlyContinue
Push-Location $OutDir
try { & tar -czf $tgz exam-deploy } finally { Pop-Location }
$hash = (Get-FileHash $tgz -Algorithm SHA256).Hash
Write-Host ("  {0}  ({1:N1} MB)  sha256={2}" -f $tgz, ((Get-Item $tgz).Length / 1MB), $hash.Substring(0, 16))
Write-Host ("  顶层条目：{0}" -f (((& tar -tzf $tgz) | Where-Object { ($_ -split '/').Count -le 2 }) -join ', '))

if ($NoUpload) {
    Write-Host "`n完成（未上传）。Workbench 手动上传这个文件：$tgz" -ForegroundColor Green
    return
}

Write-Host '=== 4/4 上传到临时托管（uguu.se，约 3 小时有效期） ===' -ForegroundColor Cyan
$out = & curl.exe -s -S --max-time 900 -F "files[]=@$tgz" 'https://uguu.se/upload?output=text' 2>&1
$url = ("$out" -split "`n" | Where-Object { $_ -match '^https?://' } | Select-Object -First 1)
if (-not $url) { throw "上传失败，返回：$out" }
Write-Host "`n下载 URL：$url" -ForegroundColor Green
Write-Host "服务器上一行装完："
Write-Host "  cd /root && curl -fL -o exam-deploy.tar.gz $url && tar -xzf exam-deploy.tar.gz && bash exam-deploy/install.sh"