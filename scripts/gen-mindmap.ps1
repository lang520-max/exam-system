# ============================================================
#  生成《在线考试系统》思维导图
#    - XMind 8 格式  .xmind（content.xml + META-INF/manifest.xml + meta.xml）
#    - Markdown 版本（可在 IDE 里直接看，也能被其他工具导入）
#
#  说明：XMind 8（v3.7.9）读取的是 content.xml；
#       新版 XMind 2020+ 读的是 content.json。本机装的是 XMind 8，
#       所以按 content.xml 生成。
# ============================================================

$ErrorActionPreference = 'Stop'

$outDir = "D:\deepseek-workspace\exam-system\docs"
New-Item -Path $outDir -ItemType Directory -Force | Out-Null

# ---------- 1. 思维导图内容（单一数据源，同时喂给 .xmind 和 .md） ----------
function N($title, $children) { [pscustomobject]@{ title = $title; children = @($children) } }

$tree = N "在线考试系统" @(
    (N "1. 项目定位" @(
        (N "从「社团管理式 CRUD」升级而来" @())
        (N "升级为：高并发 + 实时通信 + 异步处理" @())
        (N "升级前 → 升级后" @(
            (N "JDK 1.8 → 17" @())
            (N "Boot 2.7.18 → 3.2.5" @())
            (N "MyBatis → MyBatis-Plus" @())
            (N "Session 拦截器 → Spring Security + JWT" @())
            (N "无中间件 → Redis + RabbitMQ + WebSocket" @())
            (N "app+mysql → 5 服务 Compose" @())
        ))
    ))
    (N "2. 技术栈" @(
        (N "JDK 17" @())
        (N "Spring Boot 3.2.5" @())
        (N "MyBatis-Plus 3.5.7" @())
        (N "Spring Security 6.2 + jjwt 0.12.5" @())
        (N "Redis 7（倒计时 / 幂等 / 缓存）" @())
        (N "RabbitMQ 3（异步批量判分）" @())
        (N "WebSocket（实时倒计时推送）" @())
        (N "Spring AOP（操作日志 / 幂等切面）" @())
        (N "MySQL 8 生产 / H2 测试" @())
        (N "Docker Compose + Nginx" @())
    ))
    (N "3. 数据库 6 张核心表" @(
        (N "sys_user 用户" @(
            (N "role 0管理员/1教师/2学生" @())
            (N "UNIQUE(username)" @())
            (N "BCrypt 存储密码" @())
        ))
        (N "question_bank 题库" @(
            (N "组卷的抽题范围" @())
        ))
        (N "question 题目" @(
            (N "type 0单选/1多选/2判断/3填空/4简答" @())
            (N "difficulty 1简单/2中等/3困难" @())
            (N "options 存 JSON" @())
            (N "索引 idx_bank_type / idx_type_difficulty" @())
        ))
        (N "exam 考试" @(
            (N "question_snapshot 题目分值快照 ★" @())
            (N "rule_config 组卷规则" @())
            (N "status 0草稿/1进行中/2已结束" @())
        ))
        (N "exam_record 考试记录" @(
            (N "UNIQUE(exam_id,user_id) 防重复开考" @())
            (N "status 0考试中/1待判分/2已出分/3超时" @())
        ))
        (N "student_answer 答题明细" @(
            (N "UNIQUE(record_id,question_id)" @())
            (N "is_correct 2 = 待人工复核" @())
        ))
    ))
    (N "4. 四大核心难点" @(
        (N "① 智能组卷算法" @(
            (N "按题型逐条处理规则" @())
            (N "难度权重 简单:中等:困难 = 5:3:2" @())
            (N "ORDER BY RAND() LIMIT n 抽题" @())
            (N "用联合索引把候选集压小再随机排序" @())
            (N "库存不足向相邻难度借调，而非直接失败" @())
            (N "抽题结果落库为 question_snapshot" @())
        ))
        (N "② Redis + WebSocket 倒计时" @(
            (N "Redis TTL 是剩余时间的唯一真相" @())
            (N "开考 SET exam:timeout:{examId}:{userId} EX 时长" @())
            (N "ScheduledExecutorService 每秒推送" @())
            (N "断线重连从 Redis 读，不重置计时" @())
            (N "多端互踢：同 key 新连接关闭旧连接" @())
            (N "TTL 归零推送 TIMEOUT 触发自动交卷" @())
        ))
        (N "③ 幂等防重复提交" @(
            (N "防线1：Redis Lua SETNX + EXPIRE 原子占位" @())
            (N "防线2：条件更新 UPDATE WHERE status=ONGOING" @())
            (N "防线3：UNIQUE(record_id,question_id)" @())
            (N "Lua 保证占位与过期原子，避免幂等键永久残留" @())
        ))
        (N "④ RabbitMQ 异步判分" @(
            (N "交卷 → 抢占交卷权 → 落库 → 发消息" @())
            (N "GradingConsumer 只管消息与 ack 语义" @())
            (N "GradingEngine 是纯业务判分，可单测" @())
            (N "客观题自动判分" @())
            (N "主观题标记 is_correct=2 待人工复核" @())
            (N "手动 ack + 死信队列，失败重回队列" @())
            (N "无 MQ 时降级为同步判分" @())
        ))
    ))
    (N "5. 判分规则" @(
        (N "单选：精确匹配，忽略大小写与空白" @())
        (N "判断：对/正确/T/√ 归一化为 A" @())
        (N "多选：全对才得分，少选多选错选均 0 分" @())
        (N "多选：排序去重，CBA 等价于 ABC" @())
        (N "主观题：不自动给分，待人工复核" @())
        (N "未作答：0 分且仍留明细" @())
        (N "分值口径：以试卷快照为准，不是题库默认分" @())
    ))
    (N "6. 接口" @(
        (N "认证 /api/auth" @(
            (N "login 返回 Access + Refresh 双 Token" @())
            (N "register 强制学生角色" @())
            (N "refresh 用 Refresh Token 换新 Access" @())
            (N "me 当前用户（不返回密码）" @())
        ))
        (N "题库 /api/banks /api/questions" @(
            (N "增删改查 + 分页筛选" @())
            (N "删除题库前校验是否还有题目" @())
        ))
        (N "考试 /api/exams" @(
            (N "POST 智能组卷（教师）" @())
            (N "publish / end 状态流转" @())
            (N "start 开考（幂等）" @())
            (N "records/{id}/questions 取题（不含答案）" @())
            (N "submit 交卷（需 X-Request-Id）" @())
            (N "records/{id}/result 成绩单" @())
            (N "{id}/stats 统计（教师）" @())
        ))
        (N "WebSocket /ws/exam/{examId}/{userId}" @(
            (N "COUNTDOWN 剩余秒数" @())
            (N "TIMEOUT 时间到" @())
        ))
        (N "HTTP 语义" @(
            (N "401 未登录 / 403 越权" @())
            (N "404 不存在 / 409 重复交卷 / 400 参数错" @())
        ))
    ))
    (N "7. 前端页面（原生 HTML/CSS/JS，无需构建）" @(
        (N "共享基础层 js/api.js" @(
            (N "fetch 封装 + 自动带 JWT" @())
            (N "自动解 code/message/data 信封" @())
            (N "401 自动跳登录页并带 redirect" @())
            (N "Auth.requireRole 角色守卫" @())
            (N "UI.esc 防 XSS / 时间格式化 / 幂等键生成" @())
        ))
        (N "登录注册" @(
            (N "注册只创建学生，注册后自动登录" @())
        ))
        (N "学生端" @(
            (N "我的考试：开始 / 继续 / 查成绩" @())
            (N "在线答题：答题卡 + 倒计时 + 到点自动交卷" @())
            (N "我的成绩：成绩单逐题明细 + 只看错题" @())
        ))
        (N "教师端" @(
            (N "考试管理：草稿到发布到结束 + 统计" @())
            (N "智能组卷：题型+数量+分值，实时预览总分" @())
            (N "题库管理：题库与题目增删改查 + 筛选分页" @())
        ))
        (N "倒计时两层保险" @(
            (N "优先用 WebSocket 服务端推送" @())
            (N "本地按 deadlineMillis 兜底计时" @())
            (N "WebSocket 断了也不影响自动交卷" @())
        ))
    ))
    (N "8. 测试" @(
        (N "90 个用例 / 8 个测试类 / 全绿" @())
        (N "GradingEngineNormalizeTest 16 个" @())
        (N "ExamRuleServiceAllocateTest 11 个" @())
        (N "JwtUtilTest 10 个" @())
        (N "ExamFlowIntegrationTest 24 个" @())
        (N "GradingScoreSnapshotTest 3 个" @())
        (N "WebLayerTest 21 个" @())
        (N "MapperBindingTest 2 个" @())
        (N "DemoDataPasswordTest 3 个" @())
        (N "不依赖 MySQL / Redis / RabbitMQ" @())
    ))
    (N "9. 部署" @(
        (N "Docker Compose 五服务" @(
            (N "mysql:8.0" @())
            (N "redis:7-alpine" @())
            (N "rabbitmq:3-management" @())
            (N "app（JDK 17 多阶段构建）" @())
            (N "nginx:alpine 反向代理" @())
        ))
        (N "Nginx 为 /ws/ 单独配置 Upgrade 与 3600s 超时" @())
        (N "容器非 root 运行 + 健康检查" @())
    ))
    (N "10. 踩坑记录" @(
        (N "logic-delete-field=status 导致草稿试卷查不到" @())
        (N "判分误用题库分值，全对只得 40/70" @())
        (N "H2 小写化列别名，Map 取值恒为 null" @())
        (N "演示账号 BCrypt 哈希是错的" @())
        (N "业务异常返回 HTTP 200，监控失灵" @())
        (N "缺请求头被误报成 500" @())
        (N "dev 档缺 Redis/RabbitMQ Bean 启动失败" @())
        (N "不带 profile 启动会去连不存在的 RabbitMQ" @())
    ))
    (N "11. 待办" @(
        (N "Redis 幂等切面 / WebSocket 补集成测试" @())
        (N "Docker Compose 实机验证" @())
        (N "主观题人工复核的管理端页面" @())
    ))
)

# ---------- 2. 递归生成 XMind content.xml ----------
$script:idSeq = 0
function New-Id { $script:idSeq++; return "t$($script:idSeq)" }

function Escape-Xml([string]$s) {
    if ($null -eq $s) { return "" }
    return $s.Replace('&','&amp;').Replace('<','&lt;').Replace('>','&gt;').Replace('"','&quot;')
}

function Emit-TopicXml($node, [int]$depth) {
    $pad = "  " * $depth
    $id  = New-Id
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("$pad<topic id=`"$id`">")
    [void]$sb.Append("<title>$(Escape-Xml $node.title)</title>")
    if ($node.children.Count -gt 0) {
        [void]$sb.Append("<children><topics type=`"attached`">")
        foreach ($c in $node.children) { [void]$sb.Append((Emit-TopicXml $c ($depth + 1))) }
        [void]$sb.Append("</topics></children>")
    }
    [void]$sb.Append("</topic>")
    return $sb.ToString()
}

$rootId = New-Id
$contentXml = New-Object System.Text.StringBuilder
[void]$contentXml.Append('<?xml version="1.0" encoding="UTF-8" standalone="no"?>')
[void]$contentXml.Append('<xmap-content xmlns="urn:xmind:xmap:xmlns:content:2.0" version="2.0">')
[void]$contentXml.Append('<sheet id="sheet1">')
[void]$contentXml.Append("<topic id=`"$rootId`" structure-class=`"org.xmind.ui.logic.right`">")
[void]$contentXml.Append("<title>$(Escape-Xml $tree.title)</title>")
[void]$contentXml.Append('<children><topics type="attached">')
foreach ($c in $tree.children) { [void]$contentXml.Append((Emit-TopicXml $c 1)) }
[void]$contentXml.Append('</topics></children></topic>')
[void]$contentXml.Append('<title>在线考试系统</title>')
[void]$contentXml.Append('</sheet></xmap-content>')

$manifestXml = @'
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<manifest xmlns="urn:xmind:xmap:xmlns:manifest:1.0">
  <file-entry full-path="content.xml" media-type="text/xml"/>
  <file-entry full-path="meta.xml" media-type="text/xml"/>
</manifest>
'@

$metaXml = @'
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<meta xmlns="urn:xmind:xmap:xmlns:meta:2.0" version="2.0"/>
'@

# ---------- 3. 打包成 .xmind（手工建 zip，确保用 / 作为路径分隔符） ----------
$tmp = Join-Path $env:TEMP ("xmind-build-" + [guid]::NewGuid().ToString("N"))
New-Item -Path $tmp -ItemType Directory -Force | Out-Null

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $tmp "content.xml"), $contentXml.ToString(), $utf8NoBom)
[System.IO.File]::WriteAllText((Join-Path $tmp "meta.xml"), $metaXml, $utf8NoBom)
New-Item -Path (Join-Path $tmp "META-INF") -ItemType Directory -Force | Out-Null
[System.IO.File]::WriteAllText((Join-Path $tmp "META-INF\manifest.xml"), $manifestXml, $utf8NoBom)

$xmindPath = Join-Path $outDir "在线考试系统-思维导图.xmind"
if (Test-Path $xmindPath) { Remove-Item $xmindPath -Force }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipStream = [System.IO.File]::Open($xmindPath, [System.IO.FileMode]::CreateNew)
$zip = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
foreach ($entryName in @("content.xml", "meta.xml", "META-INF/manifest.xml")) {
    $src = Join-Path $tmp ($entryName -replace '/', '\')
    $entry = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $bytes = [System.IO.File]::ReadAllBytes($src)
    $es.Write($bytes, 0, $bytes.Length)
    $es.Close()
}
$zip.Dispose()
$zipStream.Close()
Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue

# ---------- 4. 同时输出 Markdown 版本 ----------
function Emit-Md($node, [int]$depth) {
    $lines = @()
    $indent = "  " * $depth
    $lines += "$indent- $($node.title)"
    foreach ($c in $node.children) { $lines += Emit-Md $c ($depth + 1) }
    return $lines
}
$md = @("# $($tree.title) 项目思维导图", "", "> 由脚本生成，与 `docs/在线考试系统-思维导图.xmind` 内容一致。", "")
$md += ($tree.children | ForEach-Object { Emit-Md $_ 0 })
$mdPath = Join-Path $outDir "在线考试系统-思维导图.md"
[System.IO.File]::WriteAllLines($mdPath, $md, $utf8NoBom)

# ---------- 5. 报告 ----------
Write-Host "生成完成：" -ForegroundColor Green
Get-ChildItem $outDir -Filter "*思维导图*" | ForEach-Object {
    Write-Host ("  {0}  ({1:N1} KB)" -f $_.Name, ($_.Length / 1KB)) -ForegroundColor Gray
}
Write-Host ("`n节点总数: {0}" -f $script:idSeq) -ForegroundColor Gray