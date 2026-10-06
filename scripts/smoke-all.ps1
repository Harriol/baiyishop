<#
  百益商城 · 一键回归冒烟（测试阶段第 1 步）

  做四件事：
    1. 起中间件（Docker，缺什么起什么）并等 Nacos / ES / RocketMQ / Seata 就绪
    2. 构建各服务 bootJar
    3. 起 8 个服务进程（gateway + 7 个业务服务），等端口就绪
    4. 依次跑 scripts/smoke 下的端到端脚本，汇总通过 / 失败

  用法：
    powershell -ExecutionPolicy Bypass -File scripts/smoke-all.ps1
    powershell ... -File scripts/smoke-all.ps1 -SkipBuild -KeepRunning
    powershell ... -File scripts/smoke-all.ps1 -Only MainPath,Seckill

  说明：脚本按 UTF-8 with BOM 保存（PowerShell 5.1 需要 BOM 才能正确解析中文）。
#>
[CmdletBinding()]
param(
    [string[]]$Only,
    [switch]$SkipBuild,
    [switch]$SkipMiddleware,
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$smokeDir = Join-Path $PSScriptRoot 'smoke'
$logDir = Join-Path $env:TEMP 'baiyishop-smoke'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\Users\Lenovo\jdk\jdk-21.0.6' }
$java = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path $java)) { throw "找不到 java：$java（请设置 JAVA_HOME）" }
$env:JAVA_HOME = $javaHome

$mysqlJar = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.mysql\mysql-connector-j" `
        -Recurse -Filter 'mysql-connector-j-*.jar' |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
if (-not $mysqlJar) { throw '找不到 mysql-connector-j（先跑一次构建让 Gradle 下载依赖）' }

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

$smokeScripts = @(
    @{ name = 'MainPath'; file = 'MainPathSmoke.java'; desc = '贯通主路径：注册→加购→下单→支付→发货→收货（经网关）' },
    @{ name = 'Search';   file = 'SearchSmoke.java';   desc = '搜索索引同步与全量重建' },
    @{ name = 'Order';    file = 'OrderSmoke.java';    desc = '下单锁库存与 Seata 全局回滚' },
    @{ name = 'Pay';      file = 'PaySmoke.java';      desc = '支付、伪造回调被拒、重复支付幂等' },
    @{ name = 'Seckill';  file = 'SeckillSmoke.java';  desc = '秒杀预扣、异步落单、取消回补' }
)

function Wait-Port([int]$port, [int]$timeoutSeconds = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue) {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-Http([string]$url, [int]$timeoutSeconds = 180) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -TimeoutSec 5 -UseBasicParsing
            if ($response.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Stop-BaiyiServices {
    Get-CimInstance Win32_Process -Filter "Name like '%java%'" |
        Where-Object { $_.CommandLine -like '*baiyishop-*SNAPSHOT.jar*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

Write-Host "=== 1/4 中间件 ===" -ForegroundColor Cyan
if (-not $SkipMiddleware) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '未找到 docker 命令' }
    $dockerReady = $false
    try { docker info *> $null; $dockerReady = $true } catch { }
    if (-not $dockerReady) {
        $exe = 'D:\tools_app\Docker\Docker Desktop.exe'
        if (Test-Path $exe) {
            Write-Host '启动 Docker Desktop…'
            Start-Process -FilePath $exe -WindowStyle Hidden
            for ($i = 0; $i -lt 30; $i++) {
                Start-Sleep -Seconds 10
                try { docker info *> $null; $dockerReady = $true; break } catch { }
            }
        }
    }
    if (-not $dockerReady) { throw 'Docker 未就绪' }
    Push-Location (Join-Path $root 'deploy')
    docker compose --env-file .env -f docker-compose.middleware.yml up -d
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw '中间件启动失败' }
    Pop-Location
    Write-Host '等 Nacos / Elasticsearch 就绪…'
    if (-not (Wait-Http 'http://localhost:8848/nacos/v1/console/health/readiness')) { throw 'Nacos 未就绪' }
    if (-not (Wait-Http 'http://localhost:9200/_cluster/health')) { throw 'Elasticsearch 未就绪' }
    if (-not (Wait-Port 9876 60)) { throw 'RocketMQ nameserver 未就绪' }
    if (-not (Wait-Port 8091 60)) { throw 'Seata TC 未就绪' }
    if (-not (Wait-Port 6379 60)) { throw 'Redis 未就绪' }
}
Write-Host '中间件就绪'

Write-Host "=== 2/4 构建 ===" -ForegroundColor Cyan
if (-not $SkipBuild) {
    Push-Location $root
    & .\gradlew.bat bootJar --console=plain
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { throw '构建失败' }
}

Write-Host "=== 3/4 启动服务 ===" -ForegroundColor Cyan
Stop-BaiyiServices
Start-Sleep -Seconds 3
foreach ($service in $services) {
    $jar = Join-Path $root "baiyishop-$($service.name)\build\libs\baiyishop-$($service.name)-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "缺少构建产物：$jar（去掉 -SkipBuild 重跑）" }
    Start-Process -FilePath $java -ArgumentList '-jar', $jar -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir "$($service.name).out.log") `
        -RedirectStandardError (Join-Path $logDir "$($service.name).err.log")
}
Write-Host '等服务端口就绪（最多 3 分钟）…'
foreach ($service in $services) {
    if (-not (Wait-Port $service.port 180)) {
        Write-Host "服务 $($service.name) (:8080) 未就绪，日志：$logDir\$($service.name).out.log" -ForegroundColor Red
        throw "服务 $($service.name) 启动失败"
    }
}
Write-Host '8 个服务已就绪'

Write-Host "=== 4/4 冒烟用例 ===" -ForegroundColor Cyan
$results = @()
foreach ($smoke in $smokeScripts) {
    if ($Only -and ($Only -notcontains $smoke.name)) { continue }
    Write-Host ""
    Write-Host "--- $($smoke.name)：$($smoke.desc) ---" -ForegroundColor Yellow
    $started = Get-Date
    & $java -cp $mysqlJar (Join-Path $smokeDir $smoke.file)
    $passed = ($LASTEXITCODE -eq 0)
    $results += [pscustomobject]@{
        name     = $smoke.name
        passed   = $passed
        duration = [int]((Get-Date) - $started).TotalSeconds
    }
}

if (-not $KeepRunning) {
    Write-Host ''
    Write-Host '停止服务进程（保留中间件）…'
    Stop-BaiyiServices
}

Write-Host ''
Write-Host '=== 汇总 ===' -ForegroundColor Cyan
$results | ForEach-Object {
    $flag = if ($_.passed) { '通过' } else { '失败' }
    Write-Host ("{0,-10} {1,-4} {2,4}s" -f $_.name, $flag, $_.duration)
}
$failedCount = ($results | Where-Object { -not $_.passed }).Count
Write-Host "共 $($results.Count) 个脚本，失败 $failedCount 个"
if ($failedCount -gt 0) { exit 1 }
