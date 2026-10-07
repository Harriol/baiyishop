# baiyishop-seckill · 秒杀服务

限时秒杀：活动与秒杀商品管理、**Redis 预扣 + 异步下单**的抢购链路、结果轮询，以及取消 / 超时的库存回补与对账补偿。

| 项 | 值 |
| --- | --- |
| 端口 | `8087` |
| schema | `baiyishop_seckill`（账号 `baiyi_seckill`） |
| 依赖 | MySQL、Redis（预扣 / 限购 / 限流）、RocketMQ、Nacos |
| 启动类 | `SeckillApplication` |

## 抢购链路

```
① POST /activities/skus/{activitySkuId}/orders
      Redis Lua 原子预扣：校验活动时间窗口 → 校验并递增「用户已购数」（限购）
                        → 扣减秒杀池剩余库存 → 扣成功才落「排队记录 + 下单消息」（同一事务）
      立即返回 { ticketId, status: "QUEUED" }
② MQ baiyishop-seckill-order  → order 服务异步创建订单（走真实下单链路）
③ order 服务回写票据结果（SUCCESS + orderNo / FAILED + 原因）
④ GET /results/{ticketId} 轮询，前端拿到订单号后跳转支付
```

抢购接口本身不做数据库写事务，**性能瓶颈在 Redis 预扣**；数据库侧只有「排队记录 + 本地消息表」一次写入。

## 对外接口

### 秒杀专区 `/api/v1/seckill`

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/activities?limit=10` | 公开 | 秒杀专区：进行中 + 即将开始（各取 limit 条） |
| GET | `/activities/{id}` | 公开 | 场次详情：起止时间、状态、倒计时秒数、秒杀商品（秒杀价、原价、划拨量、剩余量、每人限购） |
| POST | `/activities/skus/{activitySkuId}/orders` | **需登录** | 抢购；请求体 `{ "quantity": 1, "requestId": "..." }`，需 `X-Request-Id`；返回排队票据 |
| GET | `/results/{ticketId}` | 需登录 | 轮询抢购结果：`QUEUED` / `SUCCESS`（带订单号）/ `FAILED`（带失败原因） |

失败原因（前端据此提示）：售罄、超出限购、活动未开始 / 已结束、无收货地址、订单已取消（可重新抢购）、系统繁忙。

### 后台 `/api/v1/admin/seckill/activities`（`SUPER_ADMIN` / `OPERATOR`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | / | 活动分页（可按状态筛选） |
| GET | `/{id}` | 活动详情（含每个秒杀商品的划拨量 / 剩余 / 已售） |
| POST | / | 创建活动：**创建即向 inventory 申请划拨**，普通库存立即扣减；超出可售库存会被拒绝（`40005`） |
| PUT | `/{id}` | 修改活动：仅「未开始」的活动，且只支持名称与起止时间 |
| PUT | `/{id}/status` | 结束活动：**未售出库存自动回补** |
| DELETE | `/{id}` | 删除活动：未开始 / 已结束可删，删除前先回补 |

## 内部接口 `/internal/seckill`（不对外暴露）

| 方法 | 路径 | 调用方 | 说明 |
| --- | --- | --- | --- |
| POST | `/records/{ticketId}/result` | order | 回写票据结果（成功带订单号，失败带原因） |
| POST | `/orders/{orderNo}/cancelled` | order | 订单取消 / 超时 → 回补秒杀池与用户已购数 |

## 数据模型

| 表 | 说明 |
| --- | --- |
| `seckill_activity` | 活动：名称、起止时间、状态（`NOT_STARTED` / `RUNNING` / `ENDED`） |
| `seckill_activity_sku` | 活动商品：关联 SKU、秒杀价、原价、划拨量、每人限购、排序 |
| `seckill_record` | 抢购记录（票据）：`ticketId`、用户、活动 SKU、状态（`QUEUED`/`SUCCESS`/`FAILED`）、订单号、失败原因 |
| `mq_outbox` / `mq_consume_log` | 本地消息表与消费幂等 |

### Redis key 约定（前缀 `baiyishop:seckill:`）

| key | 用途 |
| --- | --- |
| `activity:{activityId}` | 活动元信息（起止时间、状态），Lua 里做时间窗口前置校验 |
| `stock:{activitySkuId}` | 预扣后的剩余秒杀库存 |
| `bought:{activitySkuId}:{userId}` | 该用户在该活动 SKU 上的已购数量（限购校验，回补时递减） |
| `rate:{userId}` | 单用户限流计数（固定窗口 1 秒） |

## 消息

| 方向 | Topic | 说明 |
| --- | --- | --- |
| 生产 | `baiyishop-seckill-order` | 预扣成功后的下单消息（与排队记录同事务写本地消息表后投递） |
| 消费 | `baiyishop-seckill-order-cancel`（组 `baiyishop-seckill-cancel`） | 订单取消 / 超时 → 回补秒杀池 |

## 定时任务与补偿

| 任务 | 频率 | 作用 |
| --- | --- | --- |
| `SeckillStatusJob` | 30s（可配 `status-scan-delay`） | 活动到点自动开始、过点自动结束并回补未售出库存（购买判定不依赖它，Lua 内会再校验时间） |
| `SeckillOutboxDispatcher` | 2s | 本地消息表投递 |
| `SeckillReconcileJob` | 周期执行 | ① **排队兜底**：`QUEUED` 超时未落单的票据先回查订单——订单其实存在就补成 `SUCCESS`，否则回补 Redis 并置 `FAILED`；② **Redis/MySQL 对账**：以 MySQL 秒杀池为准修正 Redis，且**只修「Redis 偏大」的方向**（否则会超卖），反向偏差可能是在途预扣，不盲目补 |

## 关键业务规则

- 秒杀库存**独立划拨**：创建活动时从普通库存扣减进秒杀池，未售出在活动结束 / 删除 / 订单取消时回补
- 秒杀池不变式：`remaining + sold = total`，剩余不为负
- 每人限购由后台按活动商品配置；用户维度的已购数在 Redis 中计数
- 抢购有单用户限流（默认 10 次/秒，`10005` 提示操作过于频繁）
- 秒杀订单与普通订单共用同一套下单链路（锁定库存、15 分钟支付超时、超时回补秒杀池）

## 关键配置

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `baiyishop.seckill.order-topic` | `baiyishop-seckill-order` | 下单消息 topic |
| `baiyishop.seckill.cancel-topic` | `baiyishop-seckill-order-cancel` | 取消回补 topic |
| `baiyishop.seckill.rate-limit-per-second` | `10` | 单用户每秒抢购请求上限 |
| `baiyishop.seckill.queued-timeout-minutes` | `5` | 排队记录超过该时长仍未落单，由补偿任务兜底 |
| `baiyishop.seckill.status-scan-delay` | `30s` | 活动状态推进扫描间隔 |
| `baiyishop.seckill.outbox-max-retry` | `10` | 消息投递重试上限 |

## 测试

2 个测试类 / 18 个用例：创建活动划拨与超划拨拒绝、抢购成功异步落单、售罄、超限购、
活动未开始 / 已结束、取消回补后可再次抢购、票据结果查询与幂等回写、对账补偿。

## 本地启动

```powershell
.\gradlew.bat :baiyishop-seckill:bootRun
# 需要：MySQL + Redis + RocketMQ + Nacos，且 order / inventory / product 服务已启动
```
