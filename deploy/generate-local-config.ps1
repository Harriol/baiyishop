# 百益商城 · 依据 deploy/.env 为各服务生成本地配置
#
# 目的：数据库口令、JWT 密钥等敏感项不进 Git，但本地启动（含 IDEA 直接运行）无需手工配环境变量。
# 生成的文件为 <service>/src/main/resources/application-local.yaml，已被 .gitignore 忽略。
#
# 用法：cd deploy; .\generate-local-config.ps1
#
# 本文件必须保存为 UTF-8 with BOM（Windows PowerShell 5.1 解析中文需要）

$ErrorActionPreference = "Stop"
$deployDir = $PSScriptRoot
$repoRoot = Resolve-Path (Join-Path $deployDir "..")
$envFile = Join-Path $deployDir ".env"

if (-not (Test-Path $envFile)) { throw "未找到 $envFile，请先复制 .env.example 为 .env" }

# ---- 读取 .env ----
$envMap = @{}
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $envMap[$Matches[1]] = $Matches[2].Trim() }
}

$dbHost = if ($envMap["MYSQL_HOST"]) { $envMap["MYSQL_HOST"] } else { "127.0.0.1" }
$dbPort = if ($envMap["MYSQL_PORT"]) { $envMap["MYSQL_PORT"] } else { "3306" }
$jwtUser = $envMap["JWT_USER_SECRET"]
$jwtAdmin = $envMap["JWT_ADMIN_SECRET"]
if ([string]::IsNullOrWhiteSpace($jwtUser) -or [string]::IsNullOrWhiteSpace($jwtAdmin)) {
    throw "deploy/.env 中缺少 JWT_USER_SECRET 或 JWT_ADMIN_SECRET"
}

# 服务 -> schema / 账号 / 口令环境变量；db 为空表示该服务不直连数据库
$services = @(
    @{ name = "baiyishop-gateway";   db = "";                    user = "";               pwdKey = "" },
    @{ name = "baiyishop-user";      db = "baiyishop_user";      user = "baiyi_user";      pwdKey = "MYSQL_PWD_USER" },
    @{ name = "baiyishop-product";   db = "baiyishop_product";   user = "baiyi_product";   pwdKey = "MYSQL_PWD_PRODUCT";   minio = $true },
    @{ name = "baiyishop-inventory"; db = "baiyishop_inventory"; user = "baiyi_inventory"; pwdKey = "MYSQL_PWD_INVENTORY" },
    @{ name = "baiyishop-order";     db = "baiyishop_order";     user = "baiyi_order";     pwdKey = "MYSQL_PWD_ORDER" },
    @{ name = "baiyishop-payment";   db = "baiyishop_payment";   user = "baiyi_payment";   pwdKey = "MYSQL_PWD_PAYMENT" },
    @{ name = "baiyishop-seckill";   db = "baiyishop_seckill";   user = "baiyi_seckill";   pwdKey = "MYSQL_PWD_SECKILL" }
)

foreach ($s in $services) {
    $targetDir = Join-Path $repoRoot "$($s.name)\src\main\resources"
    if (-not (Test-Path $targetDir)) {
        Write-Host "跳过 $($s.name)（目录不存在）" -ForegroundColor Yellow
        continue
    }

    $out = New-Object System.Collections.Generic.List[string]
    $out.Add("# 由 deploy/generate-local-config.ps1 生成，请勿提交（.gitignore 已忽略）")
    $out.Add("spring:")

    if ($s.db -ne "") {
        $pwd = $envMap[$s.pwdKey]
        if ([string]::IsNullOrWhiteSpace($pwd)) { throw "deploy/.env 中缺少 $($s.pwdKey)" }
        $out.Add("  # 本服务只连自己的 schema（docs/adr/ADR-007）")
        $out.Add("  datasource:")
        $out.Add("    url: jdbc:mysql://${dbHost}:${dbPort}/$($s.db)?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true")
        $out.Add("    username: $($s.user)")
        $out.Add("    password: `"$pwd`"")
    }

    $out.Add("")
    $out.Add("# JWT 密钥：用户端与后台不同，互不通用（docs/adr/ADR-003）")
    $out.Add("baiyishop:")
    $out.Add("  jwt:")
    $out.Add("    user-secret: `"$jwtUser`"")
    $out.Add("    admin-secret: `"$jwtAdmin`"")

    if ($s.minio) {
        # 图片上传（REQ-203）：桶名固定 baiyishop（桶会自动创建并设为匿名只读）
        # 取值优先级与 docker compose 一致：**进程环境变量 > deploy/.env**。
        # 本机若设了用户级 MINIO_ROOT_USER/PASSWORD，容器会用它，这里必须跟着用，否则应用连不上。
        $minioUser = $envMap["MINIO_ROOT_USER"]
        $minioPwd = $envMap["MINIO_ROOT_PASSWORD"]
        if (-not [string]::IsNullOrWhiteSpace($env:MINIO_ROOT_USER)) {
            $minioUser = $env:MINIO_ROOT_USER
            Write-Host "提示：检测到环境变量 MINIO_ROOT_USER，已覆盖 deploy/.env（compose 同此优先级）" -ForegroundColor Yellow
        }
        if (-not [string]::IsNullOrWhiteSpace($env:MINIO_ROOT_PASSWORD)) {
            $minioPwd = $env:MINIO_ROOT_PASSWORD
        }
        if ([string]::IsNullOrWhiteSpace($minioUser) -or [string]::IsNullOrWhiteSpace($minioPwd)) {
            throw "缺少 MINIO_ROOT_USER 或 MINIO_ROOT_PASSWORD（deploy/.env 或环境变量）"
        }
        $minioPort = if ($envMap["MINIO_API_PORT"]) { $envMap["MINIO_API_PORT"] } else { "9000" }
        $out.Add("")
        $out.Add("  # 对象存储：商品图 / 首页轮播图上传（REQ-203）")
        $out.Add("  minio:")
        $out.Add("    endpoint: http://localhost:$minioPort")
        $out.Add("    access-key: `"$minioUser`"")
        $out.Add("    secret-key: `"$minioPwd`"")
        $out.Add("    bucket: baiyishop")
    }
    $out.Add("")

    $content = $out -join [Environment]::NewLine
    $target = Join-Path $targetDir "application-local.yaml"
    Set-Content -Path $target -Value $content -Encoding UTF8 -NoNewline
    Write-Host "已生成 $($s.name)/src/main/resources/application-local.yaml" -ForegroundColor Green
}

Write-Host ""
Write-Host "完成。这些文件已被 .gitignore 忽略，不会进入版本库。" -ForegroundColor Green
