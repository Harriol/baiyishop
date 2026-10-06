<#
  百益商城 · 性能压测（测试阶段第 2 步，NFR-01）

  前置：8 个服务已在运行（可用 scripts/smoke-all.ps1 -SkipBuild -KeepRunning -Only None 拉起）、
        中间件已就绪、JMeter 已安装在 -JmeterHome（默认 D:\tools_app\apache-jmeter-5.6.3）。

  流程：
    1. 准备压测数据：1000 个用户+地址、1000 个用户令牌、1 个秒杀活动（划拨 1000）、基线商品 id
    2. 基线压测：200 并发 60s，混合只读接口（商品详情 / 搜索 / 首页 / 分类树），目标 P95 ≤ 300ms
    3. 秒杀压测：1000 并发抢 1000 份库存（每人限购 1），验收不超卖
    4. 汇总：从 JTL 计算各接口的并发数、吞吐、P95、错误率与秒杀业务码分布

  产物：docs/load-test/（JTL + JMeter HTML 报告 + 汇总），本地保留（docs 不入库）
#>
[CmdletBinding()]
param(
    [int]$BaselineThreads = 200,
    [int]$BaselineDurationSeconds = 60,
    [int]$SeckillThreads = 1000,
    [int]$SeckillRampupSeconds = 5,
    [int]$Users = 1000,
    [string]$JmeterHome = 'D:\tools_app\apache-jmeter-5.6.3',
    [switch]$OnlyPrepare,
    [switch]$SkipPrepare,
    [switch]$SkipBaseline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jmeterDir = Join-Path $PSScriptRoot 'jmeter'
$jmeter = Join-Path $JmeterHome 'bin\jmeter.bat'
$workDir = Join-Path $env:TEMP 'baiyishop-loadtest'
$resultDir = Join-Path $root 'docs\load-test'
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\Users\Lenovo\jdk\jdk-21.0.6' }
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'

if (-not (Test-Path $jmeter)) { throw "找不到 JMeter：$jmeter（用 -JmeterHome 指定安装目录）" }
New-Item -ItemType Directory -Force -Path $workDir, $resultDir | Out-Null

# ---------- 0. 服务可用性检查 ----------
foreach ($port in 8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087) {
    if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue)) {
        throw "服务端口 $port 未就绪；先执行 scripts/smoke-all.ps1 -SkipBuild -KeepRunning -Only None 拉起服务"
    }
}
Write-Host '8 个服务端口均就绪' -ForegroundColor Cyan

# ---------- 1. 准备数据 ----------
if (-not $SkipPrepare) {
    Write-Host "=== 准备压测数据（用户 $Users 个）===" -ForegroundColor Cyan
    $mysqlJar = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.mysql\mysql-connector-j" `
            -Recurse -Filter 'mysql-connector-j-*.jar' |
            Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
    & $java "-Dusers=$Users" -cp $mysqlJar (Join-Path $jmeterDir 'PrepareLoadTest.java')
    if ($LASTEXITCODE -ne 0) { throw '压测数据准备失败' }
}
if ($OnlyPrepare) { Write-Host '仅准备数据，结束'; exit 0 }

$props = @{}
Get-Content (Join-Path $workDir 'loadtest.properties') | ForEach-Object {
    $kv = $_ -split '=', 2
    if ($kv.Count -eq 2) { $props[$kv[0]] = $kv[1] }
}
$activitySkuId = $props['activitySkuId']
$tokensCsv = $props['tokensCsv']
$productsCsv = $props['productsCsv']
Write-Host "秒杀活动 activitySkuId=$activitySkuId，令牌文件=$tokensCsv"

function Invoke-Jmeter([string]$plan, [string]$jtl, [string]$reportDir, [string[]]$extra) {
    if (Test-Path $reportDir) { Remove-Item -LiteralPath $reportDir -Recurse -Force }
    # JMeter 要求结果文件必须不存在或为空，否则直接报错退出
    if (Test-Path $jtl) { Remove-Item -LiteralPath $jtl -Force }
    $arguments = @('-n', '-t', (Join-Path $jmeterDir $plan), '-l', $jtl, '-e', '-o', $reportDir,
        '-j', (Join-Path $workDir ("$plan.jmeter.log"))) + $extra
    Write-Host ("运行 JMeter：" + ($arguments -join ' ')) -ForegroundColor DarkGray
    & $jmeter @arguments | Select-String -Pattern 'summary|Err:|error|Writing' | ForEach-Object { $_.Line.Trim() }
}

function Get-JtlSummary([string]$jtl) {
    $rows = Import-Csv -LiteralPath $jtl
    $summary = @()
    foreach ($group in ($rows | Group-Object label)) {
        $elapsed = $group.Group | ForEach-Object { [double]$_.elapsed } | Sort-Object
        $count = $elapsed.Count
        $errors = ($group.Group | Where-Object { $_.success -ne 'true' }).Count
        $p95Index = [Math]::Max(0, [Math]::Ceiling($count * 0.95) - 1)
        $times = @($group.Group | ForEach-Object { [double]$_.timeStamp })
        $spanSeconds = 1.0
        if ($times.Count -gt 1) {
            $maxTs = ($times | Measure-Object -Maximum).Maximum
            $minTs = ($times | Measure-Object -Minimum).Minimum
            $spanSeconds = [Math]::Max((($maxTs - $minTs) / 1000.0), 0.001)
        }
        $summary += [pscustomobject]@{
            label      = $group.Name
            samples    = $count
            errors     = $errors
            errorPct   = [math]::Round(100.0 * $errors / $count, 2)
            avgMs      = [math]::Round(($elapsed | Measure-Object -Average).Average, 1)
            p95Ms      = [math]::Round($elapsed[$p95Index], 1)
            maxMs      = [math]::Round($elapsed[$count - 1], 1)
            throughput = [math]::Round($count / $spanSeconds, 1)
        }
    }
    return $summary
}

# ---------- 2. 基线压测 ----------
if (-not $SkipBaseline) {
    Write-Host "=== 基线压测：$BaselineThreads 并发 × ${BaselineDurationSeconds}s ===" -ForegroundColor Cyan
    $baselineJtl = Join-Path $resultDir 'baseline.jtl'
    Invoke-Jmeter 'api-baseline.jmx' $baselineJtl (Join-Path $resultDir 'baseline-report') @(
        "-Jthreads=$BaselineThreads", "-Jduration=$BaselineDurationSeconds", "-JproductsCsv=$productsCsv")
    $baseline = Get-JtlSummary $baselineJtl
    $baseline | Format-Table -AutoSize | Out-String | Write-Host
}

# ---------- 3. 秒杀压测 ----------
Write-Host "=== 秒杀压测：$SeckillThreads 并发抢 $SeckillThreads 份库存 ===" -ForegroundColor Cyan
$seckillJtl = Join-Path $resultDir 'seckill.jtl'
Invoke-Jmeter 'seckill-buy.jmx' $seckillJtl (Join-Path $resultDir 'seckill-report') @(
    "-Jthreads=$SeckillThreads", "-Jrampup=$SeckillRampupSeconds", "-JactivitySkuId=$activitySkuId",
    "-JtokensCsv=$tokensCsv", '-Jsample_variables=bizCode')
$seckill = Get-JtlSummary $seckillJtl
$seckill | Format-Table -AutoSize | Out-String | Write-Host

$codes = Import-Csv -LiteralPath $seckillJtl | Group-Object bizCode |
    Select-Object Name, Count | Sort-Object Count -Descending
Write-Host '业务码分布（0 受理成功 / 70004 售罄 / 70005 超限购 / 10005 限流）:' -ForegroundColor Cyan
$codes | Format-Table -AutoSize | Out-String | Write-Host

# ---------- 4. 异步落单结果与「不超卖」判定 ----------
Write-Host "=== 等异步落单跑完并核对库存（最多 120s）===" -ForegroundColor Cyan
$mysqlJar = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.mysql\mysql-connector-j" `
        -Recurse -Filter 'mysql-connector-j-*.jar' |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
& $java -cp $mysqlJar (Join-Path $jmeterDir 'VerifyLoadTest.java') $activitySkuId $SeckillThreads 2>&1 |
    ForEach-Object { $_ }

Write-Host ''
Write-Host "产物：$resultDir（JTL + HTML 报告）" -ForegroundColor Cyan
