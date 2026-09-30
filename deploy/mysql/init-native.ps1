# 百益商城 · 在**本机原生 MySQL** 上初始化 6 个 schema 与专用账号
# 依据 docs/database.md 2 章与 docs/adr/ADR-007（账号按 schema 授权，阻止跨库访问）
#
# 用法：
#   .\init-native.ps1 -RootPassword '你的root密码'
# 说明：
#   - 幂等：全部使用 CREATE ... IF NOT EXISTS，可重复执行
#   - 只创建/授权本项目的 6 个 schema，不触碰其他已有数据库
#   - 客户端优先取本机 MySQL 安装目录下的 mysql.exe，其次取 PATH 中的 mysql

param(
    [string]$HostName = "127.0.0.1",
    [int]$Port = 3306,
    [string]$RootUser = "root",
    [Parameter(Mandatory = $true)][string]$RootPassword,
    [string]$ClientPath = "D:\Mysql-8.0.16-winx64\mysql-8.0.16-winx64\bin\mysql.exe"
)

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "..\.env"

if (-not (Test-Path $envFile)) {
    throw "未找到 $envFile，请先复制 deploy/.env.example 为 deploy/.env"
}

# ---- 读取 .env 中的 schema 账号密码 ----
$envMap = @{}
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $envMap[$Matches[1]] = $Matches[2].Trim() }
}

$schemas = @(
    @{ db = "baiyishop_user";      user = "baiyi_user";      pwd = $envMap["MYSQL_PWD_USER"] },
    @{ db = "baiyishop_product";   user = "baiyi_product";   pwd = $envMap["MYSQL_PWD_PRODUCT"] },
    @{ db = "baiyishop_inventory"; user = "baiyi_inventory"; pwd = $envMap["MYSQL_PWD_INVENTORY"] },
    @{ db = "baiyishop_order";     user = "baiyi_order";     pwd = $envMap["MYSQL_PWD_ORDER"] },
    @{ db = "baiyishop_payment";   user = "baiyi_payment";   pwd = $envMap["MYSQL_PWD_PAYMENT"] },
    @{ db = "baiyishop_seckill";   user = "baiyi_seckill";   pwd = $envMap["MYSQL_PWD_SECKILL"] }
)

foreach ($s in $schemas) {
    if ([string]::IsNullOrWhiteSpace($s.pwd)) { throw "deploy/.env 中缺少 $($s.db) 的密码配置" }
}

# ---- 定位客户端 ----
$mysqlExe = if (Test-Path $ClientPath) { $ClientPath } else { (Get-Command mysql -ErrorAction SilentlyContinue).Source }
if (-not $mysqlExe) { throw "未找到 mysql 客户端，请用 -ClientPath 指定路径" }
Write-Host "使用客户端: $mysqlExe"
Write-Host "目标服务器: $HostName:$Port"

# ---- 生成并执行 SQL ----
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("SET NAMES utf8mb4;")
foreach ($s in $schemas) {
    [void]$sb.AppendLine("CREATE DATABASE IF NOT EXISTS \`$($s.db)\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
    [void]$sb.AppendLine("CREATE USER IF NOT EXISTS '$($s.user)'@'%' IDENTIFIED BY '$($s.pwd)';")
    [void]$sb.AppendLine("CREATE USER IF NOT EXISTS '$($s.user)'@'localhost' IDENTIFIED BY '$($s.pwd)';")
    [void]$sb.AppendLine("GRANT ALL PRIVILEGES ON \`$($s.db)\`.* TO '$($s.user)'@'%';")
    [void]$sb.AppendLine("GRANT ALL PRIVILEGES ON \`$($s.db)\`.* TO '$($s.user)'@'localhost';")
}
[void]$sb.AppendLine("FLUSH PRIVILEGES;")

$env:MYSQL_PWD = $RootPassword   # 避免密码出现在进程命令行里
try {
    $sb.ToString() | & $mysqlExe -h $HostName -P $Port -u $RootUser --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw "mysql 客户端返回码 $LASTEXITCODE" }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host "初始化完成，共 $($schemas.Count) 个 schema 与账号：" -ForegroundColor Green
foreach ($s in $schemas) { Write-Host ("  {0,-20} -> {1}" -f $s.db, $s.user) }
