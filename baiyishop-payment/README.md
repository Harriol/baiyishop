# baiyishop-payment · 支付服务

支付单的创建与查询：**渠道策略**（微信 / 支付宝）、回调**验签 + 幂等**、支付成功后发布领域事件。
渠道为**模拟实现**，但接口形态、签名校验、回调幂等、金额核对都按真实网关的对接方式设计。

| 项 | 值 |
| --- | --- |
| 端口 | `8086` |
| schema | `baiyishop_payment`（账号 `baiyi_payment`） |
| 依赖 | MySQL、RocketMQ（生产支付成功事件）、Nacos |
| 启动类 | `PaymentApplication` |

## 对外接口 `/api/v1/payments`

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | / | 登录用户 | 发起支付（**必须带 `X-Request-Id`**）：校验订单可支付与金额一致，创建支付单并返回渠道支付参数（`payParams`）与过期时间 |
| POST | `/callback/{channel}` | **公开**（靠验签） | 渠道支付结果回调：先验签，再幂等，最后才改业务；应答使用渠道自己的报文形状而非统一信封 |
| GET | `/{paymentNo}` | 登录用户 | 查询支付单（只能查自己的） |
| GET | `/by-order/{orderNo}` | 登录用户 | 按订单号查支付单 |
| POST | `/{paymentNo}/mock-pay` | 登录用户 | **模拟渠道一键支付**（仅本地联调）：内部按真实规则签名后调用回调处理，验签与幂等路径完全一致 |

发起支付请求：

```json
{ "orderNo": "2026100619202716695908", "channel": "WECHAT" }
```

响应 `data`：

```json
{ "paymentNo": "P...", "channel": "WECHAT", "amount": 19800,
  "payParams": { "prepayId": "...", "nonceStr": "...", "sign": "..." }, "expireAt": "..." }
```

## 数据模型

| 表 | 说明 |
| --- | --- |
| `payment` | 支付单：支付单号、订单号、渠道、金额、状态（`PENDING`/`SUCCESS`/`FAILED`/`CLOSED`）、渠道交易号、支付时间、过期时间 |
| `payment_callback_log` | 回调报文与处理结果留痕（排查重复回调、验签失败） |
| `mq_outbox` | 本地消息表（支付成功事件的可靠投递） |

## 关键设计

1. **回调安全**：`X-Pay-Signature` 验签失败一律拒绝（`60004`），验签通过后按渠道交易号与支付单号做**幂等**，
   重复回调不会重复触发业务。
2. **金额核对**：回调金额与支付单金额不一致直接拒绝（`60003`），防止渠道侧金额被篡改。
3. **只允许自己查自己**：支付单查询按「当前登录用户 + 支付单号」过滤，越权返回同一错误码，不泄露资源是否存在。
4. **事件驱动**：支付成功后写本地消息表，由定时任务投递 `baiyishop-payment-success`，
   由订单服务消费后把订单推进到「待发货」并扣减库存。
5. **模拟渠道**：`MockChannelClient` 按渠道生成签名与交易号；`mock-pay` 入口由开关控制，**生产必须关闭**。

## 消息

| 方向 | Topic | 说明 |
| --- | --- | --- |
| 生产 | `baiyishop-payment-success` | 支付成功事件（订单号、支付单号、渠道、金额、支付时间），由 `PaymentOutboxDispatcher` 定时投递，失败重试上限 10 次 |

## 关键配置

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `baiyishop.payment.mock-secret` | 本地示例值 | 模拟渠道签名密钥；真实渠道密钥走密钥管理，不入库、不进日志 |
| `baiyishop.payment.mock-pay-enabled` | `true` | 「模拟一键支付」入口开关，**生产环境必须设为 false** |
| `baiyishop.payment.outbox-max-retry` | `10` | 事件投递重试上限 |
| `baiyishop.mq.payment-success-topic` | `baiyishop-payment-success` | 支付成功事件 topic |

## 测试

2 个测试类 / 12 个用例：发起支付的金额一致性与重复发起、伪造回调验签失败（`60004`）、
模拟支付成功、重复回调幂等不重复扣减、查询越权、事件投递。

## 本地启动

```powershell
.\gradlew.bat :baiyishop-payment:bootRun
# 需要：MySQL + RocketMQ + Nacos，且 order 服务已启动（发起支付时要校验订单）
```
