# baiyishop-order · 订单服务

交易链路的核心：购物车、结算试算、**下单**（跨服务全局事务 + 幂等）、订单状态机、超时取消与自动确认收货，
以及后台的发货与备注。

| 项 | 值 |
| --- | --- |
| 端口 | `8085` |
| schema | `baiyishop_order`（账号 `baiyi_order`） |
| 依赖 | MySQL、RocketMQ、Seata（**全局事务发起方**）、Nacos |
| 启动类 | `OrderApplication` |

## 对外接口

### 购物车 `/api/v1/carts`（需登录）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | / | 购物车（含失效标记、勾选合计与数量，金额由服务端计算） |
| POST | `/items` | 加入购物车（同 SKU 数量累加，超出单品限购拒绝） |
| PUT | `/items/{id}` | 修改数量 / 勾选状态 |
| DELETE | `/items/{id}` | 删除条目 |
| PUT | `/checked` | 全选 / 取消全选 |

> 写操作统一返回**整份购物车**，前端无需再补一次查询。

### 订单 `/api/v1/orders`（需登录）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/settle` | 结算试算（只读）：购物车勾选项或立即购买，返回明细、金额、默认地址，并标出**失效商品** |
| POST | / | 提交订单（**必须带 `X-Request-Id`**） |
| GET | / | 我的订单分页（`status` 筛选 + 分页） |
| GET | `/{orderNo}` | 订单详情：明细快照、收货信息、金额、状态流转日志 |
| PUT | `/{orderNo}/cancel` | 取消订单（仅待付款） |
| PUT | `/{orderNo}/receive` | 确认收货（仅待收货） |

提交订单请求体：

```json
{ "source": "CART", "cartItemIds": [1, 2], "addressId": 10, "remark": "工作日送达" }
{ "source": "BUY_NOW", "skuId": 100, "quantity": 1, "addressId": 10 }
```

- `source=CART` **必须显式传 `cartItemIds`**（不会自动回退到「购物车里已勾选的条目」）
- `source=BUY_NOW` 走 `skuId + quantity`
- `source=SECKILL` 由秒杀链路创建，直接调用会被拒绝
- 同一 `X-Request-Id` 重复提交返回**首次订单号**（数据库唯一键兜底并发重复提交）
- 下单成功后自动清除已结算的购物车条目

### 后台订单 `/api/v1/admin/orders`

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | / | 超管 / 运营 / 客服 | 订单分页：订单号、状态、用户、下单时间区间 |
| GET | `/{orderNo}` | 超管 / 运营 / 客服 | 订单详情 + 地址快照 + 状态日志 + 备注 |
| POST | `/{orderNo}/ship` | 超管 / 运营 | 发货（录入运单号），成功后安排 **7 天自动确认收货** |
| GET | `/{orderNo}/notes` | 超管 / 运营 / 客服 | 备注列表 |
| POST | `/{orderNo}/notes` | 超管 / 运营 / 客服 | 添加备注（**客服唯一的写权限**） |

## 内部接口 `/internal/orders`（不对外暴露）

| 方法 | 路径 | 调用方 | 说明 |
| --- | --- | --- | --- |
| GET | `/{orderNo}/payable` | payment | 发起支付前校验订单可支付性与应付金额 |
| GET | `/by-ticket/{ticketId}` | seckill | 按抢购票据查订单号（前端轮询结果用） |

## 订单状态机

```
                    ┌─ 15 分钟未支付（延时消息 + 兜底扫描）─┐
待付款 PENDING_PAYMENT ──支付成功事件──> 待发货 PENDING_SHIPMENT
        │                                              │
        └──用户取消 / 超时──> 已取消 CANCELLED            └─后台发货─> 待收货 PENDING_RECEIPT
                                                                        │
                                                        用户确认收货 / 7 天自动确认
                                                                        ↓
                                                                   已完成 COMPLETED
```

每次状态变更都会写一条 `order_status_log`（来源状态、目标状态、操作方、原因），订单详情页据此渲染时间线。

## 数据模型

| 表 | 说明 |
| --- | --- |
| `cart_item` | 购物车条目（用户 + SKU + 数量 + 勾选） |
| `order` | 订单主表：订单号、状态、金额（商品/运费/应付）、来源、超时时间、各阶段时间戳、取消原因 |
| `order_item` | 订单明细**快照**（商品名、SKU 名、图、单价、数量、小计），下单后不随商品变更而变 |
| `order_status_log` | 状态流转日志 |
| `order_note` | 后台备注（含操作管理员与时间） |
| `order_request` | 请求幂等表（`X-Request-Id` 唯一） |
| `undo_log` | Seata AT 回滚日志（全局事务发起方所需） |
| `mq_outbox` / `mq_consume_log` | 本地消息表与消费幂等 |

## 消息

| 方向 | Topic | 说明 |
| --- | --- | --- |
| 生产 | `baiyishop-order-timeout` | 下单时发送**延时消息**，到期未支付则自动取消并释放库存 |
| 生产 | `baiyishop-order-auto-receive` | 发货时发送延时消息，7 天后自动确认收货 |
| 生产 | `baiyishop-seckill-order-cancel` | 秒杀订单取消 / 超时，通知 seckill 回补秒杀池 |
| 消费 | `baiyishop-payment-success`（组 `baiyishop-order-payment`） | 支付成功 → 订单转为待发货 + 扣减库存 |
| 消费 | `baiyishop-seckill-order`（组 `baiyishop-order-seckill`） | 秒杀抢购成功 → 异步创建订单，回写票据结果 |

延时消息可能丢失，因此 `OrderMaintenanceJob` 每分钟兜底扫描一次到期未支付的订单与到期未收货的订单。

## 关键业务规则

- 下单在**全局事务**中完成：写订单 + 调 inventory 锁库存，任一失败整体回滚
- 单品限购数量默认 99（购物车累加与下单数量都受约束）
- 未支付自动取消 15 分钟；发货后自动确认收货 7 天
- 运费按「全场包邮」实现（`freightAmount = 0`），金额计算统一在服务端
- 结算试算会标出失效商品（已下架 / 已删除），**有效商品为空时直接拒绝**下单

## 关键配置

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `baiyishop.order.max-quantity-per-sku` | `99` | 单品限购数量 |
| `baiyishop.order.pay-timeout` | `15m` | 未支付自动取消时长 |
| `baiyishop.order.auto-receive-after` | `7d` | 发货后自动确认收货时长 |
| `baiyishop.order.timeout-scan-delay` | `1m` | 兜底扫描间隔 |
| `baiyishop.order.outbox-max-retry` | `10` | 本地消息表投递重试上限 |

## 测试

3 个测试类 / 29 个用例：购物车增删改与限购、结算试算与失效商品、下单锁库存与全局事务回滚、
并发重复提交幂等、超时取消释放库存、发货与自动收货、后台备注权限边界。

## 本地启动

```powershell
.\gradlew.bat :baiyishop-order:bootRun
# 需要：MySQL + RocketMQ + Nacos + Seata TC，且 user / product / inventory 服务已启动
```
