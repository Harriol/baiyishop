# 百益商城 · BaiyiShop

微服务架构的 **B2C 自营商城**（单商家自营），个人学习 / 作品集项目。覆盖需求 → 设计 → 开发 → 测试的完整过程，
包含用户端（Web + 微信小程序原型）、运营后台，以及 8 个 Spring Cloud 业务服务。

> 说明：本项目面向学习与作品展示，业务规则按真实电商来设计（下单锁库存、15 分钟未支付自动取消、
> 发货后 7 天自动确认收货、秒杀独立库存池、支付回调验签与幂等），支付渠道为**模拟实现**（接口按真实规范设计）。

---

## 目录

- [功能清单](#功能清单)
- [技术概览](#技术概览)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [接口约定](#接口约定)
- [测试与质量](#测试与质量)
- [目录结构](#目录结构)
- [已知边界](#已知边界)

---

## 功能清单

### 用户端（Web，`prototype/*.html`，14 页）

| 模块 | 能力 |
| --- | --- |
| 首页 | 左侧**三级分类导航（悬停展开）**、轮播图、公告、金刚区、秒杀入口、楼层推荐（楼层可按分类自动拉取并单独配置排序维度） |
| 分类浏览 | 三级分类树 + 商品列表 + 销量/上新/价格升降排序 + 分页 |
| 搜索 | Elasticsearch 检索：关键词 + 分类/品牌/价格区间筛选；综合 / 销量 / 价格升降排序 |
| 商品详情 | 多图图集、SKU 选择、实时库存、规格参数、富文本详情、同类推荐 |
| 购物车 | 勾选、改量、失效商品置灰、全选、合计试算（金额由服务端算） |
| 下单支付 | 结算试算 → 提交订单（15 分钟支付倒计时）→ 模拟微信/支付宝支付 → 支付结果 |
| 订单中心 | 按状态筛选、订单详情（状态时间线）、取消订单、确认收货、再次购买 |
| 收货地址 | 增删改、设为默认（手机号列表脱敏，编辑需重填） |
| 账号 | 注册、账号密码登录、注销；游客可浏览与搜索，**下单必须登录** |

### 微信小程序（原型，`prototype/miniapp/*.html`，6 页）

首页 / 分类 / 商品详情 / 购物车 / 我的订单 / 我的；登录方式为**微信授权**（本地走 Mock 微信客户端，code 即 openid），
与 Web 端**共用同一套后端接口**。

### 运营后台（`prototype/admin/*.html`，10 页）

| 模块 | 能力 |
| --- | --- |
| 工作台 | 待发货 / 待付款 / 库存预警 / 在售商品待办数字与列表 |
| 订单管理 | 按订单号/状态查询、订单详情、发货（录入运单号）、订单备注（客服唯一写权限） |
| 商品管理 | 筛选（分类按含子分类匹配）、新增/编辑（三级分类级联、品牌、**图片本地上传**、SKU、参数）、上下架、逻辑删除 |
| 分类与品牌 | 三级分类树增删改、显示/隐藏、品牌增删改与启停（有子分类或被引用时拒绝删除） |
| 库存管理 | 库存列表、库存流水（变更前后值/原因/操作人）、库存预警与关闭、库存调整 |
| 首页配置 | 轮播图 / 公告 / 金刚区 / 楼层四类配置整份保存，楼层可单独配置排序维度 |
| 秒杀活动 | 活动与秒杀商品管理、创建即划拨库存、结束活动自动回补未售出库存 |
| 管理员与角色 | 管理员列表与权限清单、角色权限矩阵（写操作接口不在本期范围） |

**RBAC**：超级管理员 / 运营 / 客服三种角色。客服仅可查询订单与添加备注，不可发货；越权访问返回 403，
前端按权限码渲染菜单与按钮。

### 关键业务规则

- 下单**锁定库存**，15 分钟未支付自动取消并释放；发货后 7 天自动确认收货
- 下单、发起支付、抢购都带 `X-Request-Id` 幂等键，重复提交返回首次结果
- 秒杀库存从普通库存**独立划拨**（划拨即扣减），未售出自动回补；抢购走 Redis 预扣 + MQ 异步下单
- 订单状态机：`待付款 → 待发货 → 待收货 → 已完成`，以及 `已取消`
- 金额在服务端一律以**分**为单位存储与传输

---

## 技术概览

| 类别 | 选型 | 说明 |
| --- | --- | --- |
| 语言 / 构建 | Java 21、Gradle 9.7.1（Kotlin DSL） | 依赖版本集中在 `gradle/libs.versions.toml` |
| 应用框架 | Spring Boot **4.1.1** | 开启虚拟线程（`spring.threads.virtual`） |
| 微服务 | Spring Cloud **2025.1.3** + Spring Cloud Alibaba **2025.1.0.0** | 网关、服务发现（Nacos） |
| 注册 / 配置 | Nacos 2.4.3 | 服务注册与发现 |
| 持久层 | MyBatis-Plus **3.5.17** | 逻辑删除、自动填充、防全表更新拦截 |
| 数据库 | MySQL 8.0 + Flyway | **按服务拆 schema**，账号按 schema 授权，DDL 由 Flyway 管理 |
| 缓存 | Redis 7（AOF） | 登录失败计数、令牌黑名单、秒杀预扣与限购、消费幂等 |
| 消息 | RocketMQ 5.3.1 | 延时消息（超时取消/自动收货）、领域事件、本地消息表投递 |
| 分布式事务 | Seata 2.0.0（AT 模式） | 下单跨服务写（订单 + 库存）保持原子 |
| 搜索 | Elasticsearch 8.11.3 | 商品检索；客户端版本与服务器保持一致 |
| 对象存储 | MinIO | 商品图片（本期后端未提供上传接口，图片为手填 URL） |
| 鉴权 | JWT（jjwt 0.12.7） | 用户端与后台**双密钥、双受众**，服务端二次校验 |
| 前端 | 原生 HTML/CSS/JS + Vite（开发服务器与代理） | 30 个页面，无框架、无打包步骤 |
| 测试 | JUnit 5 + Mockito、JaCoCo 0.8.13、JMeter 5.6 | 185 个用例、覆盖率 83.5%、端到端冒烟脚本与压测脚本 |

---

## 系统架构

### 服务与端口

| 服务 | 端口 | 职责 | 独立 schema |
| --- | --- | --- | --- |
| `baiyishop-gateway` | 8080 | 统一入口：路由、JWT 鉴权与白名单、遮蔽 `/internal/**`、透传身份头 | — |
| `baiyishop-user` | 8081 | 注册登录（含微信授权）、用户资料、收货地址、后台认证与 RBAC | `baiyishop_user` |
| `baiyishop-product` | 8082 | 分类、品牌、商品与 SKU、参数模板、首页配置、商品变更事件 | `baiyishop_product` |
| `baiyishop-search` | 8083 | ES 检索与索引同步（增量消费 + 全量重建） | —（仅 Redis + ES） |
| `baiyishop-inventory` | 8084 | 库存权威数据、库存流水、库存预警、秒杀库存池 | `baiyishop_inventory` |
| `baiyishop-order` | 8085 | 购物车、结算试算、下单、订单状态机、超时取消与自动收货 | `baiyishop_order` |
| `baiyishop-payment` | 8086 | 发起支付、渠道回调验签与幂等、支付查询 | `baiyishop_payment` |
| `baiyishop-seckill` | 8087 | 秒杀活动、Redis 预扣与限购、异步下单、结果查询与回补 | `baiyishop_seckill` |

### 公共模块

| 模块 | 内容 |
| --- | --- |
| `baiyishop-common-core` | 统一响应体 `Result` / 分页 `PageResult` / `ErrorCode` / `BizException` / 脱敏工具 |
| `baiyishop-common-web` | 全局异常处理、traceId 过滤器、内部服务调用客户端（`InternalApiClient`，透传 traceId 与 Seata XID）、Jackson 时间配置 |
| `baiyishop-common-security` | JWT 签发与校验、BCrypt、`UserContext`、`@RequiresRole` + 角色拦截器、令牌黑名单接口 |
| `baiyishop-common-data` | MyBatis-Plus 配置（分页、乐观锁、防全表更新）、审计字段自动填充 |
| `baiyishop-common-mq` | 消息能力的预留模块（当前各服务的本地消息表与消费幂等各自实现） |

### 设计要点

1. **服务拆分**：按业务域拆 8 个服务，商品域合并了首页配置（读多写少、同源数据），购物车并入订单域。
2. **跨服务一致性**：同步写用 **Seata AT** 保证原子（下单 = 写订单 + 锁库存）；Redis / ES / MQ 这类
   最终一致的链路，用「**幂等 + 本地消息表 + 兜底对账**」收敛，不做分布式强一致。
3. **鉴权**：网关只放行白名单并校验令牌，服务端**再校验一次**（不信任上游）；用户端与后台令牌使用不同密钥，
   互相不通用；`/internal/**` 对外一律 404，内部接口不做令牌校验（由链路本身保证）。
4. **数据库拆分**：一个服务一个 schema，账号按 schema 授权，从权限层阻止跨库查询；服务间只通过接口交互。
5. **秒杀**：库存池归属 inventory 服务；抢购先 Redis 预扣（Lua 原子 + 每人限购 + 每用户限流）→ 返回排队票据 →
   MQ 异步落单 → 前端轮询票据结果；取消/超时回补，排队超时由补偿任务兜底。
6. **搜索同步**：商品变更写本地消息表（同一事务）→ 定时投递 `baiyishop-product-changed` → search 服务消费并写 ES；
   索引用别名，全量重建时建新索引、切别名、删旧索引，重建期间查询不中断。

---

## 快速开始

### 前置条件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 构建与运行都要求 21（Gradle toolchain 已锁定） |
| Docker Desktop | 任意较新版本 | 跑中间件（Redis / Nacos / RocketMQ / ES / MinIO / Seata） |
| MySQL | 8.0（**本机原生**） | 项目默认连本机 3306 的 MySQL，不用容器版 |
| Node.js | 18+ | 只用于前端开发服务器（Vite） |
| JMeter | 5.6（可选） | 只在跑压测时需要 |

### 步骤

```powershell
# 0) JDK：确认 java -version 是 21
$env:JAVA_HOME = 'C:\path\to\jdk-21'          # 按本机路径修改

# 1) 中间件环境变量（首次）
cd deploy
Copy-Item .env.example .env                    # 按需改密码

# 2) 初始化 6 个 schema 与专用账号（幂等，只动本项目的库）
.\mysql\init-native.ps1 -RootPassword '你的MySQL root密码'

# 3) 用 .env 生成各服务的 application-local.yaml（含库口令与 JWT 密钥，已被 gitignore）
.\generate-local-config.ps1

# 4) 起中间件（Redis / Nacos / RocketMQ / ES / MinIO / Seata）
docker compose --env-file .env -f docker-compose.middleware.yml up -d
cd ..

# 5) 构建并起 8 个服务（一键脚本会等端口就绪；已有构建产物时加 -SkipBuild 可跳过构建）
powershell -ExecutionPolicy Bypass -File scripts/smoke-all.ps1 -SkipMiddleware -Only None -KeepRunning
#   -SkipMiddleware = 中间件已在跑；-Only None = 只起服务、不跑冒烟脚本
#   也可以单模块跑： .\gradlew.bat :baiyishop-user:bootRun

# 6) 前端
cd prototype
npm install
npm run dev                                    # http://localhost:5173
```

| 入口 | 地址 |
| --- | --- |
| 用户端 Web | http://localhost:5173/index.html |
| 运营后台 | http://localhost:5173/admin/login.html |
| 小程序原型 | http://localhost:5173/miniapp/index.html |

### 本地演示账号

> 只存在于本地数据库，用于演示；生产环境请自行创建账号并停用它们。

| 端 | 账号 | 密码 | 角色 |
| --- | --- | --- | --- |
| 后台 | `demo_admin` | `Admin@2026` | 超级管理员 |
| 后台 | `demo_operator` | `Admin@2026` | 运营 |
| 后台 | `demo_service` | `Admin@2026` | 客服（仅查单 + 备注） |
| 前台 | `buyer01` | `Demo@2026` | 普通用户（已带地址与示例订单） |

### 一键脚本

| 脚本 | 用途 |
| --- | --- |
| `scripts/smoke-all.ps1` | 起中间件 → 构建 → 起 8 个服务 → 跑全部端到端冒烟脚本（`-SkipBuild` / `-SkipMiddleware` / `-KeepRunning` / `-Only`） |
| `scripts/reset-demo-data.ps1` | 清掉冒烟/压测数据并播种一份可演示数据（分类/商品/库存/首页配置/订单/秒杀） |
| `scripts/run-load-test.ps1` | 造 1000 用户 + 秒杀活动，跑基线（200 并发）与秒杀（1000 并发）压测并产出报告 |

---

## 接口约定

### 统一响应体

```json
{ "code": 0, "message": "success", "data": { }, "success": true }
```

`code = 0` 表示成功（HTTP 状态码为 200）；业务失败通过 `code` 区分，`message` 是可直接展示给用户的中文提示。

| 码段 | 含义 | 示例 |
| --- | --- | --- |
| `0` | 成功 | — |
| `10001~10009` | 通用 | 参数错误、登录失效、无权限、资源不存在、限流、系统繁忙、文件类型不支持、文件过大、上传失败 |
| `20001~20009` | 用户与账号 | 账号密码错误、账号锁定、已注册、微信授权失败、地址不存在 |
| `30001~30013` | 商品域 | 分类不存在/有子分类/被引用、商品不存在/已下架、参数模板与参数项校验 |
| `40001~40005` | 库存域 | 库存不足、库存记录不存在、调整数量非法、秒杀池不存在、划拨超可售 |
| `50001~50007` | 订单域 | 订单不存在、状态不支持、购物车条目失效/为空/不存在、超限购、缺地址 |
| `60001~60004` | 支付域 | 支付单不存在、不可支付、金额不一致、回调验签失败 |
| `70001~70007` | 秒杀域 | 活动不存在、未开始、已结束、已售罄、超限购、SKU 未上架、记录不存在 |

完整错误码枚举见 `baiyishop-common-core` 的 `ErrorCode`。

### 其他约定

- **鉴权**：`Authorization: Bearer <token>`；公开路径由网关白名单控制（注册/登录、分类/品牌/商品/首页/搜索、
  秒杀只读接口、支付渠道回调）
- **幂等**：下单、发起支付、抢购需要 `X-Request-Id`，重复提交返回首次结果
- **分页**：请求 `page` / `size`，响应 `{page, size, total, list}`
- **金额**：一律为「分」（整数）
- **内部接口**：`/internal/**` 只允许服务间调用，网关对外返回 404

---

## 测试与质量

| 项 | 结果 |
| --- | --- |
| 单元 / 集成测试 | 8 个服务 **31 个测试类 / 197 个用例全绿**（`./gradlew test`，另公共模块 5 个用例） |
| 覆盖率（JaCoCo 行覆盖） | 整体 **81.5%**，核心模块 ≥ 80% |
| 端到端冒烟 | 6 个脚本：主链路、冒烟清单（44 项）、搜索同步、订单与库存、支付、秒杀（`scripts/smoke-all.ps1`） |
| 压测 | JMeter 脚本 + 报告（基线 200 并发、秒杀 1000 并发）；功能正确性通过，性能未达 P95 ≤ 300ms 目标（学习项目无生产环境，未继续优化） |

```powershell
.\gradlew.bat test                       # 单测 + 覆盖率
.\gradlew.bat :baiyishop-order:test      # 单模块
powershell -ExecutionPolicy Bypass -File scripts/smoke-all.ps1 -SkipBuild -SkipMiddleware
```

---

## 目录结构

```
baiyishop/
├── baiyishop-gateway/           # 网关（路由 + 鉴权）
├── baiyishop-user/              # 用户、地址、认证、RBAC
├── baiyishop-product/           # 分类、品牌、商品、参数、首页配置
├── baiyishop-search/            # ES 检索与索引同步
├── baiyishop-inventory/         # 库存、流水、预警、秒杀库存池
├── baiyishop-order/             # 购物车、订单、状态机
├── baiyishop-payment/           # 支付与回调
├── baiyishop-seckill/           # 秒杀活动与抢购
├── baiyishop-common-*/          # core / web / security / data / mq 公共模块
├── deploy/                      # 中间件 compose、初始化脚本、本地配置生成
│   ├── docker-compose.middleware.yml
│   ├── generate-local-config.ps1
│   └── mysql/ elasticsearch/ rocketmq/ seata/
├── prototype/                   # 前端 30 个页面（用户端 / 后台 / 小程序原型）
│   ├── vite.config.mjs
│   └── assets/js/               # api.js / store.js / util.js / ui.js
├── scripts/                     # 一键脚本：冒烟回归、压测、演示数据
│   ├── smoke-all.ps1  run-load-test.ps1  reset-demo-data.ps1
│   └── smoke/ jmeter/ demo/
├── gradle/libs.versions.toml     # 依赖版本单一来源
└── build.gradle.kts settings.gradle.kts
```

---

## 已知边界

这些是有意为之的范围外或简化项，不是缺陷：

- **图片存储**：后台图片（商品主图/图集、首页轮播图）上传到 **MinIO**，桶不存在时自动创建并设为匿名只读；
  图片加载失败时前端会回退到本地占位图
- **管理员与角色**：只有查询（分配角色 / 重置密码未实现）
- **商品详情的 `available` 字段**：接口暂未回填，前端另行调用库存接口获取可售数量
- **支付**：模拟渠道（微信/支付宝测试语义），本地提供「模拟一键支付」入口，生产必须关闭该开关
- **不做**：多商家、优惠券/积分、商品评价、售后退款、物流轨迹、客服 IM、数据报表、发票
- **文档**：需求、架构、数据库、API、ADR、测试报告等设计文档在本机 `docs/` 下维护，**未随仓库发布**

---

## 相关文档

| 位置 | 内容 |
| --- | --- |
| `prototype/README.md` | 前端页面清单、联调约定、如何起前端 |
| `deploy/README.md` | 中间件容器、端口、原生 MySQL 初始化、Seata 本地连接方式 |
| `scripts/README.md` | 冒烟回归、压测、演示数据脚本的用法 |
| 各服务 `README.md` | 该服务的接口、数据模型、消息、配置与业务规则 |
