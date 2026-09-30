#!/bin/bash
# 百益商城 · MySQL 初始化：创建 6 个 schema 与各自的专用账号
# 依据 docs/database.md 2 章与 docs/adr/ADR-007（账号按 schema 授权，阻止跨库访问）
# 该脚本由 MySQL 官方镜像在首次初始化数据目录时自动执行。
set -euo pipefail

note() { echo "[baiyishop-init] $*"; }

run_sql() {
  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "$1"
}

create_schema() {
  local schema="$1" user="$2" pwd="$3"
  run_sql "CREATE DATABASE IF NOT EXISTS \`${schema}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  run_sql "CREATE USER IF NOT EXISTS '${user}'@'%' IDENTIFIED BY '${pwd}';"
  run_sql "GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${user}'@'%';"
  note "已就绪 schema=${schema} user=${user}"
}

create_schema baiyishop_user      baiyi_user      "${MYSQL_PWD_USER}"
create_schema baiyishop_product   baiyi_product   "${MYSQL_PWD_PRODUCT}"
create_schema baiyishop_inventory baiyi_inventory "${MYSQL_PWD_INVENTORY}"
create_schema baiyishop_order     baiyi_order     "${MYSQL_PWD_ORDER}"
create_schema baiyishop_payment   baiyi_payment   "${MYSQL_PWD_PAYMENT}"
create_schema baiyishop_seckill   baiyi_seckill   "${MYSQL_PWD_SECKILL}"

run_sql "FLUSH PRIVILEGES;"
note "初始化完成：6 个 schema 与 6 个专用账号"
