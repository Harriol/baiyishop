# 百益商城 · 依据 deploy/.env 为各服务生成本地配置
#
# 目的：数据库口令等敏感项不进 Git，但本地启动（含 IDEA 直接运行）无需手工配环境变量。
# 生成的文件为 <service>/src/main/resources/application-local.yaml，已被 .gitignore 忽略。
#
# 用法：cd deploy; .\generate-local-config.ps1
#
# 本文件必须保存为 UTF-8 with BOM（Windows PowerShell 5.1 解析中文需要）

$ErrorActionPreference = "Stop"
$deployDir = $PSScriptRoot
$repoRoot = Resolve-Path (Join-Path $deployDir "..")
$envFile = Join-Path $deployDir ".env"

if (-not (Test-Path $envFile)) {
    throw "未找到 $envFile，请先复制 .env.example 为 .env"
}

# ---- 读取 .env ----
$envMap = @{}
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $envMap[$Matches[1]] = $Matches[2].Trim() }
}

$dbHost = if ($envMap["MYSQL_HOST"]) { $envMap["MYSQL_HOST"] } else { "127.0.0.1" }
$dbPort = if ($envMap["MYSQL_PORT"]) { $envMap["MYSQL_PORT"] } else { "3306" }

# 服务 -> schema / 账号 / 口令环境变量（gateway 与 search 不直接连库）
$services = @(
    @{ name = "baiyishop-user";      db = "baiyishop_user";      user = "baiyi_user";      pwdKey = "MYSQL_PWD_USER" },
    @{ name = "baiyishop-product";   db = "baiyishop_product";   user = "baiyi_product";   pwdKey = "MYSQL_PWD_PRODUCT" },
    @{ name = "baiyishop-inventory"; db = "baiyishop_inventory"; user = "baiyi_inventory"; pwdKey = "MYSQL_PWD_INVENTORY" },
    @{ name = "baiyishop-order";     db = "baiyishop_order";     user = "baiyi_order";     pwdKey = "MYSQL_PWD_ORDER" },
    @{ name = "baiyishop-payment";   db = "baiyishop_payment";   user = "baiyi_payment";   pwdKey = "MYSQL_PWD_PAYMENT" },
    @{ name = "baiyishop-seckill";   db = "baiyishop_seckill";   user = "baiyi_seckill";   pwdKey = "MYSQL_PWD_SECKILL" }
)

foreach ($s in $services) {
    $pwd = $envMap[$s.pwdKey]
    if ([string]::IsNullOrWhiteSpace($pwd)) { throw "deploy/.env 中缺少 $($s.pwdKey)" }

    $targetDir = Join-Path $repoRoot "$($s.name)\src\main\resources"
    if (-not (Test-Path $targetDir)) {
        Write-Host "跳过 $($s.name)（目录不存在）" -ForegroundColor Yellow
        continue
    }

    $content = @(
        "# 由 deploy/generate-local-config.ps1 生成，请勿提交（.gitignore 已忽略）",
        "# 数据源与 schema 对应关系见 docs/adr/ADR-007：本服务只连自己的 schema",
        "spring:",
        "  datasource:",
        "    url: jdbc:mysql://${dbHost}:${dbPort}/$($s.db)?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true",
        "    username: $($s.user)",
        "    password: `"$pwd`"",
        ""
    ) -join [Environment]::NewLine

    $target = Join-Path $targetDir "application-local.yaml"
    Set-Content -Path $target -Value $content -Encoding UTF8 -NoNewline
    Write-Host "已生成 $($s.name)/src/main/resources/application-local.yaml" -ForegroundColor Green
}

Write-Host ""
Write-Host "完成。这些文件已被 .gitignore 忽略，不会进入版本库。" -ForegroundColor Green
