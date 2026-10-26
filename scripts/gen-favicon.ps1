# ============================================================
#  生成多尺寸 favicon.ico（内含 16/32/48 三个 PNG）
#
#  为什么不用 SVG：SecurityConfig 只放行了 /favicon.ico，
#  加 /favicon.svg 需要改 Java 并重启应用。而 favicon.ico
#  已经在放行名单里，生成一个真实文件即可立刻生效。
# ============================================================
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function New-IconPng([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.Clear([System.Drawing.Color]::Transparent)

    $s = $size / 32.0   # 以 32 为基准等比缩放

    # 圆角蓝色底
    $r = [int](7 * $s)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $w = $size - 1; $h = $size - 1
    $path.AddArc(0, 0, $r * 2, $r * 2, 180, 90)
    $path.AddArc($w - $r * 2, 0, $r * 2, $r * 2, 270, 90)
    $path.AddArc($w - $r * 2, $h - $r * 2, $r * 2, $r * 2, 0, 90)
    $path.AddArc(0, $h - $r * 2, $r * 2, $r * 2, 90, 90)
    $path.CloseFigure()
    $blue = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(37, 99, 235))
    $g.FillPath($blue, $path)

    # 白色「试卷」
    $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $g.FillRectangle($white, [single](8 * $s), [single](6.5 * $s), [single](16 * $s), [single](19 * $s))

    # 三条浅蓝文字线
    $lightBlue = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(147, 197, 253))
    foreach ($y in @(11, 15)) {
        $g.FillRectangle($lightBlue, [single](11 * $s), [single]($y * $s), [single](10 * $s), [single](1.8 * $s))
    }
    $g.FillRectangle($lightBlue, [single](11 * $s), [single](19 * $s), [single](6 * $s), [single](1.8 * $s))

    # 绿色对勾
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(22, 163, 74), [single](1.9 * $s))
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pts = @(
        (New-Object System.Drawing.PointF([single](18.6 * $s), [single](19.4 * $s))),
        (New-Object System.Drawing.PointF([single](20.1 * $s), [single](20.9 * $s))),
        (New-Object System.Drawing.PointF([single](23.1 * $s), [single](17.7 * $s)))
    )
    $g.DrawLines($pen, $pts)

    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = $ms.ToArray()
    $ms.Dispose(); $g.Dispose(); $bmp.Dispose()
    return ,$bytes
}

$sizes = @(16, 32, 48)
$pngs = @()
foreach ($s in $sizes) { $pngs += ,(New-IconPng $s) }

# ---- 组装 ICO：ICONDIR(6B) + N × ICONDIRENTRY(16B) + 各 PNG 数据 ----
$ms = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter($ms)

$bw.Write([UInt16]0)              # reserved
$bw.Write([UInt16]1)              # type = 1 (icon)
$bw.Write([UInt16]$sizes.Count)   # image count

$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $s = $sizes[$i]
    $bw.Write([Byte]$(if ($s -ge 256) { 0 } else { $s }))   # width
    $bw.Write([Byte]$(if ($s -ge 256) { 0 } else { $s }))   # height
    $bw.Write([Byte]0)            # 调色板数
    $bw.Write([Byte]0)            # reserved
    $bw.Write([UInt16]1)          # planes
    $bw.Write([UInt16]32)         # bit count
    $bw.Write([UInt32]$pngs[$i].Length)
    $bw.Write([UInt32]$offset)
    $offset += $pngs[$i].Length
}
foreach ($p in $pngs) { $bw.Write($p) }

$bw.Flush()
$ico = $ms.ToArray()
$bw.Dispose(); $ms.Dispose()

$targets = @(
    "D:\deepseek-workspace\exam-system\src\main\resources\static\favicon.ico",
    "D:\deepseek-workspace\exam-system\target\classes\static\favicon.ico"
)
foreach ($t in $targets) {
    $dir = Split-Path $t -Parent
    if (-not (Test-Path $dir)) { New-Item -Path $dir -ItemType Directory -Force | Out-Null }
    [System.IO.File]::WriteAllBytes($t, $ico)
}

Write-Host ("已生成 favicon.ico：{0} 字节，含 {1} 个尺寸（{2}）" -f $ico.Length, $sizes.Count, ($sizes -join '/')) -ForegroundColor Green
$targets | ForEach-Object { Write-Host "  -> $_" -ForegroundColor Gray }