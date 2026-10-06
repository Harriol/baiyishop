<#
.SYNOPSIS
  百益商城 · 演示数据重置：清掉冒烟/压测残留数据，播种一份"能看的"演示数据。

.DESCRIPTION
  1) 清理：删掉冒烟与压测留下的分类 / 商品 / 库存 / 订单 / 支付 / 秒杀 / 用户（保留 RBAC 字典与后台登录）
  2) 播种：三级分类树、品牌、参数模板、27 个商品与默认 SKU、库存（含 3 条低库存预警）、首页配置
             （轮播 / 公告 / 金刚区 / 楼层）
  3) 走真实接口：重建 ES 索引、注册演示买家并下 3 笔不同状态的订单（待付款 / 待发货 / 待收货）、
             建 2 个秒杀场次（库存划拨由服务端完成）

.PARAMETER SkipReset
  只播种，不清库。

.PARAMETER SkipHttp
  只做 SQL 部分（8 个服务没起时用）。注意：ES 索引不会重建，搜索会查不到新商品。

.PARAMETER SkipHealthCheck
  不预检 8 个服务是否就绪。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/reset-demo-data.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/reset-demo-data.ps1 -SkipHttp
#>
param(
    [switch]$SkipReset,
    [switch]$SkipHttp,
    [switch]$SkipHealthCheck
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$services = @(
    @{ name = 'gateway';   port = 8080 },
    @{ name = 'user';      port = 8081 },
    @{ name = 'product';   port = 8082 },
    @{ name = 'search';    port = 8083 },
    @{ name = 'inventory'; port = 8084 },
    @{ name = 'order';     port = 8085 },
    @{ name = 'payment';   port = 8086 },
    @{ name = 'seckill';   port = 8087 }
)

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\Users\Lenovo\jdk\jdk-21.0.6' }
$java = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path $java)) { throw "找不到 java：$java（请设置 JAVA_HOME）" }
$env:JAVA_HOME = $javaHome

$mysqlJar = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.mysql\mysql-connector-j" `
        -Recurse -Filter 'mysql-connector-j-*.jar' |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
if (-not $mysqlJar) { throw '找不到 mysql-connector-j（先跑一次构建让 Gradle 下载依赖）' }

if (-not $SkipHttp -and -not $SkipHealthCheck) {
    Write-Host '预检：8 个服务健康状态…'
    $down = @()
    foreach ($svc in $services) {
        $healthy = $false
        try {
            $response = Invoke-WebRequest -Uri ("http://localhost:{0}/actuator/health" -f $svc.port) -TimeoutSec 5 -UseBasicParsing
            # actuator 返回 application/vnd.spring-boot.actuator+json，PS 5.1 下 Content 可能是 byte[]，需先解码
            $text = if ($response.Content -is [byte[]]) {
                [System.Text.Encoding]::UTF8.GetString($response.Content)
            } else {
                [string]$response.Content
            }
            $healthy = ($response.StatusCode -eq 200 -and $text -match '"status"\s*:\s*"UP"')
        } catch { }
        if ($healthy) {
            Write-Host ("  [OK]   " + $svc.name)
        } else {
            Write-Host ("  [DOWN] " + $svc.name)
            $down += $svc.name
        }
    }
    if ($down.Count -gt 0) {
        throw ("以下服务未就绪：" + ($down -join ', ') +
            "`n先启动后端：powershell -ExecutionPolicy Bypass -File scripts/smoke-all.ps1 -SkipBuild -SkipMiddleware -Only None -KeepRunning")
    }
}

$demoArgs = @()
if ($SkipReset) { $demoArgs += '-SkipReset' }
if ($SkipHttp) { $demoArgs += '-SkipHttp' }

$demoScript = Join-Path $PSScriptRoot 'demo\DemoData.java'
if (-not (Test-Path $demoScript)) { throw "找不到演示数据脚本：$demoScript" }

Write-Host ''
Write-Host ("java  ：" + $java)
Write-Host ("驱动  ：" + (Split-Path -Leaf $mysqlJar))
Write-Host ''

& $java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $mysqlJar $demoScript @demoArgs
if ($LASTEXITCODE -ne 0) { throw "演示数据脚本执行失败（exit=$LASTEXITCODE）" }
