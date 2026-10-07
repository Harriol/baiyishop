# baiyishop-gateway · 网关

系统统一入口，基于 **Spring Cloud Gateway（WebFlux）**。承担路由、鉴权、身份注入与内部接口遮蔽，
**不直连数据库**，也不承载业务逻辑。

| 项 | 值 |
| --- | --- |
| 端口 | `8080` |
| 数据源 | — |
| 依赖 | Nacos（服务发现）、`baiyishop-common-security`（令牌校验） |
| 启动类 | `GatewayApplication` |
| 健康检查 | `GET /actuator/health`（另暴露 `info`、`gateway`） |

## 职责

1. **路由**：把 `/api/v1/**` 按业务域转发到对应服务（`lb://baiyishop-xxx`，经 Nacos 负载均衡）。
2. **鉴权**：白名单直接放行，其余路径要求 `Authorization: Bearer <token>` 并校验签名与受众。
3. **遮蔽内部接口**：`/internal/**` 对外一律返回 404，服务间接口不暴露到公网。
4. **身份注入**：校验通过后剥掉客户端自带的 `X-User-Id` / `X-User-Role`，再按令牌重新注入，防伪造。
5. **链路追踪**：`TraceIdGlobalFilter` 生成 / 透传 `X-Trace-Id`，与各服务的日志格式串联。

## 路由表

| 路由 id | 路径前缀 | 目标服务 |
| --- | --- | --- |
| `user-service` | `/api/v1/auth/**`、`/api/v1/users/**`、`/api/v1/addresses/**`、`/api/v1/admin/auth/**`、`/api/v1/admin/admins/**`、`/api/v1/admin/roles/**`、`/api/v1/admin/permissions/**` | `baiyishop-user` |
| `product-service` | `/api/v1/categories/**`、`/api/v1/brands/**`、`/api/v1/products/**`、`/api/v1/home/**`、`/api/v1/admin/categories/**`、`/api/v1/admin/brands/**`、`/api/v1/admin/products/**`、`/api/v1/admin/params/**`、`/api/v1/admin/home/**`、`/api/v1/admin/uploads/**` | `baiyishop-product` |
| `search-service` | `/api/v1/search/**` | `baiyishop-search` |
| `inventory-service` | `/api/v1/inventory/**`、`/api/v1/admin/inventory/**` | `baiyishop-inventory` |
| `order-service` | `/api/v1/carts/**`、`/api/v1/orders/**`、`/api/v1/admin/orders/**` | `baiyishop-order` |
| `payment-service` | `/api/v1/payments/**` | `baiyishop-payment` |
| `seckill-service` | `/api/v1/seckill/**`、`/api/v1/admin/seckill/**` | `baiyishop-seckill` |

> 注意：Spring Cloud Gateway 5.x 的配置前缀是 `spring.cloud.gateway.server.webflux`（4.x 是 `spring.cloud.gateway`）。

## 鉴权流程（`JwtAuthGlobalFilter`）

```
请求 → /internal/** ?
        是 → 404（不暴露内部接口）
        否 → 命中 public-paths 白名单 ?
               是 → 放行（不解析令牌）
               否 → 要求 Bearer 令牌
                     ├─ 路径以 /api/v1/admin/ 开头 → 按 audience=ADMIN 校验（后台密钥）
                     └─ 其余                       → 按 audience=USER  校验（用户密钥）
                    ├─ 通过 → 剥掉客户端 X-User-Id / X-User-Role，注入令牌中的身份，转发
                    └─ 失败 → 10002 未登录 / 10003 无权限
```

## 公开路径白名单

配置项 `baiyishop.gateway.public-paths`，支持 `路径` 或 `方法:路径` 两种写法：

```yaml
baiyishop:
  gateway:
    public-paths:
      - /actuator/**
      - /api/v1/auth/register          # 注册 / 登录 / 微信登录 / 刷新令牌
      - /api/v1/auth/login
      - /api/v1/auth/wechat-login
      - /api/v1/auth/refresh
      - /api/v1/admin/auth/login       # 后台登录
      - /api/v1/categories/**          # 前台只读：分类 / 品牌 / 商品 / 首页 / 搜索 / 库存
      - /api/v1/brands/**
      - /api/v1/products/**
      - /api/v1/home/**
      - /api/v1/search/**
      - /api/v1/inventory/**
      - /api/v1/payments/callback/**   # 渠道回调靠验签，不靠令牌
      - GET:/api/v1/seckill/activities # 秒杀专区只读；抢购接口需要登录，故意不放行
      - GET:/api/v1/seckill/activities/*
```

## 关键配置

| 配置 | 说明 |
| --- | --- |
| `baiyishop.jwt.user-secret` / `admin-secret` | 两套 JWT 密钥（≥32 字节），由 `deploy/generate-local-config.ps1` 从 `deploy/.env` 生成到 `application-local.yaml`，**不进仓库** |
| `NACOS_SERVER_ADDR` / `NACOS_NAMESPACE` | 注册中心地址与命名空间 |
| `baiyishop.gateway.public-paths` | 鉴权白名单 |

## 本地启动

```powershell
# 前置：Nacos 已启动，且各业务服务已注册
.\gradlew.bat :baiyishop-gateway:bootRun
```

## 测试

`src/test` 下 2 个测试类 / 5 个用例，覆盖：内部接口遮蔽、身份头防伪造、受众判定（用户令牌访问后台接口必须失败）、
公开路径放行。
