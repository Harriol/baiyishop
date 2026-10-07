# baiyishop-inventory · 库存服务

库存的**权威数据源**：可售/锁定库存、库存流水、库存预警，以及**秒杀独立库存池**
（秒杀库存从普通库存划拨而来，池子归属本服务）。

| 项 | 值 |
| --- | --- |
| 端口 | `8084` |
| schema | `baiyishop_inventory`（账号 `baiyi_inventory`） |
| 依赖 | MySQL、Nacos、Seata（全局事务参与方 RM） |
| 启动类 | `InventoryApplication` |

## 对外接口

### 前台（公开）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/inventory/skus/{skuId}/available` | 单个 SKU 可售数量（商品详情页展示库存） |
| GET | `/api/v1/inventory/skus/available?skuIds=1,2` | 批量可售数量 |

### 后台（`/api/v1/admin/inventory`，`SUPER_ADMIN` / `OPERATOR`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | / | 库存分页（可按 `skuId` / `productId` 筛选，或只看预警 `onlyAlert=true`） |
| PUT | `/{skuId}/adjust?productId=` | 调整库存：正数补货、负数减库；**每次调整写一条流水**（变更前后值 + 原因 + 操作人） |
| GET | `/flows` | 库存流水分页（可按 SKU、类型筛选） |
| GET | `/alerts?status=OPEN` | 库存预警列表 |
| PUT | `/alerts/{id}/close` | 关闭预警 |

## 内部接口 `/internal/inventory`（不对外暴露）

| 方法 | 路径 | 调用方 | 说明 |
| --- | --- | --- | --- |
| POST | `/lock` | order | 下单锁定库存（可售 → 锁定） |
| POST | `/deduct` | order | 支付成功后扣减锁定库存 |
| POST | `/release` | order | 取消 / 超时释放锁定库存（幂等） |
| GET | `/skus/{skuId}` | order | 单个 SKU 库存 |
| GET | `/skus/available` | order | 批量可售数量（结算试算） |
| POST | `/seckill/allocate` | seckill | 秒杀库存划拨（普通库存立即扣减，写入秒杀池） |
| POST | `/seckill/return` | seckill | 活动结束 / 删除时回补未售出库存 |
| POST | `/seckill/deduct` | seckill | 秒杀下单成功后在池内扣减并记已售 |
| GET | `/seckill/pool/{activitySkuId}` | seckill / 后台 | 秒杀池剩余量与流水 |

## 数据模型

| 表 | 说明 |
| --- | --- |
| `inventory` | 库存权威表：`available`（可售）、`locked`（锁定）、预警阈值、乐观锁版本 |
| `inventory_flow` | 库存流水：类型（`LOCK`/`DEDUCT`/`UNLOCK`/`ADJUST`/`ALLOCATE`/`RETURN`/`ROLLBACK`）、变更前后值、原因、操作人；`biz_key` 唯一约束是**幂等落点** |
| `stock_alert` | 库存预警（`OPEN` / `CLOSED`，`(sku_id, status)` 唯一） |
| `seckill_stock_pool` | 秒杀库存池：`total` / `remaining` / `sold`，不变式 **remaining + sold = total**，剩余不为负 |
| `seckill_stock_flow` | 秒杀池流水（`ALLOCATE`/`DEDUCT`/`RETURN`/`ROLLBACK`），同样以 `biz_key` 保证幂等 |
| `undo_log` | Seata AT 模式回滚日志（全局事务参与方所需） |
| `mq_outbox` / `mq_consume_log` | 消息相关表（预留） |

## 关键业务规则

- 可售库存**不允许为负**；调整后为负直接拒绝（`40003`）
- 库存调整数量为 0 或非数字一律拒绝；SKU 必须存在（`skuId > 0`）
- 锁定 / 扣减 / 释放都带业务幂等键，重复调用不会重复扣减
- 秒杀划拨数量**不得超过当前可售库存**（`40005`）；活动结束或删除时未售出部分自动回补普通库存
- 库存低于预警阈值时产生预警记录，可在后台关闭

## 关键配置

| 配置 | 说明 |
| --- | --- |
| `seata.enabled` / `seata.service.grouplist.default` | 作为下单全局事务的参与方接入 TC（本地用 grouplist 直连 `127.0.0.1:8091`） |
| `spring.datasource.url` | 只连 `baiyishop_inventory`，账号无跨库权限 |

## 测试

3 个测试类 / 23 个用例：锁定/扣减/释放与幂等、库存不足边界、调整数量与原因校验、预警触发与关闭、
秒杀池划拨与不变式、回补后可再次划拨。

## 本地启动

```powershell
.\gradlew.bat :baiyishop-inventory:bootRun
# 需要：MySQL + Nacos + Seata TC（下单链路会开启全局事务）
```
