# baiyishop-user · 用户服务

账号体系与用户资料：注册登录（账号密码 / 微信授权）、令牌签发与注销、收货地址、后台管理员认证与 **RBAC 三角色**。

| 项 | 值 |
| --- | --- |
| 端口 | `8081` |
| schema | `baiyishop_user`（账号 `baiyi_user`，Flyway 管理表结构） |
| 依赖 | MySQL、Redis（登录失败计数 + 令牌黑名单）、Nacos |
| 启动类 | `UserApplication` |

## 对外接口

### 认证 `/api/v1/auth`（公开）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/register` | 账号密码注册（账号 4~20 位字母数字下划线，密码 6~32 位），成功即返回令牌 |
| POST | `/login` | 账号密码登录；连续失败 5 次锁定 10 分钟（Redis 计数） |
| POST | `/wechat-login` | 微信授权登录（小程序）；首次登录自动注册 |
| POST | `/refresh` | 用 refreshToken 换新令牌（access 2 小时 / refresh 7 天） |
| POST | `/logout` | 注销：access token 的 jti 进 Redis 黑名单 |

> 本地联调时 `baiyishop.wechat.mock-enabled=true`（默认）会启用 `MockWechatAuthClient`，**code 即 openid**，
> 不依赖真实微信测试号；接入真实微信时置为 false。

### 用户资料 `/api/v1/users`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/me` | 当前用户资料（手机号脱敏返回） |

### 收货地址 `/api/v1/addresses`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | / | 地址列表（默认地址排首位） |
| POST | / | 新增地址；第一条自动设为默认 |
| PUT | `/{id}` | 修改地址 |
| DELETE | `/{id}` | 删除；删掉默认地址时自动把最新一条置为默认 |
| PUT | `/{id}/default` | 设为默认地址 |

### 后台认证与账号 `/api/v1/admin/**`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/admin/auth/login` | 公开 | 管理员登录（后台令牌，与前台令牌**互不通用**） |
| GET | `/admin/auth/me` | 三种角色 | 当前管理员信息 + 权限码列表，前端据此渲染菜单 |
| GET | `/admin/admins` | `SUPER_ADMIN` | 管理员列表（含角色与权限码） |

## 内部接口 `/internal/users`（不对外暴露）

| 方法 | 路径 | 调用方 | 说明 |
| --- | --- | --- | --- |
| GET | `/addresses/{id}` | order | 下单时取地址快照 |
| GET | `/addresses/default/{userId}` | order | 结算试算时取默认地址 |
| GET | `/admins/{id}` | 审计 / 排查 | 管理员基本信息 |

## 数据模型

| 表 | 说明 |
| --- | --- |
| `user` | 用户（账号、BCrypt 密码、昵称、头像、手机号、状态；小程序用户可无密码） |
| `user_wechat` | 微信绑定（openid 唯一，同一 openid 只绑一个账号） |
| `user_address` | 收货地址（是否默认） |
| `admin` | 后台管理员 |
| `role` / `permission` / `role_permission` / `admin_role` | RBAC 字典与关联（由可重复迁移 `R__seed_role_and_permission.sql` 维护，幂等） |

### 角色与权限

| 角色 | 权限 |
| --- | --- |
| `SUPER_ADMIN` 超级管理员 | 全部（含管理员与角色管理） |
| `OPERATOR` 运营 | 分类/品牌/商品/参数/首页/库存/秒杀 + 订单（含发货、备注），**无**管理员与角色 |
| `SERVICE` 客服 | 仅 `order:read` + `order:note`（查单 + 备注，不可发货、不可退款） |

权限码形如 `product:read`、`inventory:write`、`order:ship`，服务端用 `@RequiresRole` + 拦截器二次校验。

## 关键安全设计

- 密码只存 **BCrypt** 哈希；手机号在接口返回前统一脱敏（`MaskingUtils`）
- 前台与后台令牌使用**不同密钥、不同受众**（`Audience.USER` / `Audience.ADMIN`），互不通用
- 登录失败计数 key `baiyishop:user:login:fail:*`；令牌黑名单 key `baiyishop:user:token:blacklist:{jti}`
- 地址相关接口只按「当前登录用户 + 地址 id」查询，不泄露资源是否存在（越权返回同一错误码）

## 测试

4 个测试类 / 27 个用例：注册登录与锁定、令牌刷新与注销、地址增删改与默认地址、微信登录注册、RBAC 权限边界。

## 本地启动

```powershell
.\gradlew.bat :baiyishop-user:bootRun
# 需要：MySQL（schema 已初始化）+ Redis + Nacos，且 application-local.yaml 已生成
```
