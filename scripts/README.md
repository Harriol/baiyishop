# scripts：一键回归入口

测试阶段用脚本，不属于业务代码，但**提交进仓库**——回归要能被别人（和未来的自己）一条命令复现。

## 用法

```powershell
# 全流程：起中间件 → 构建 → 起 8 个服务 → 跑全部冒烟脚本 → 停服务
powershell -ExecutionPolicy Bypass -File scripts/smoke-all.ps1

# 常见变体
powershell ... -File scripts/smoke-all.ps1 -SkipBuild          # 已有构建产物，跳过构建
powershell ... -File scripts/smoke-all.ps1 -KeepRunning        # 跑完不停止服务（接着手工点页面用）
powershell ... -File scripts/smoke-all.ps1 -Only MainPath,Pay  # 只跑指定脚本
powershell ... -File scripts/smoke-all.ps1 -SkipMiddleware     # 中间件已在跑
```

前置条件：JDK 21（`JAVA_HOME`，默认 `D:\Users\Lenovo\jdk\jdk-21.0.6`）、Docker Desktop、本机原生 MySQL（root/root）、
`deploy/.env`（首次从 `.env.example` 复制）。MySQL 数据由各服务自己的 Flyway 迁移维护。

## 冒烟脚本

| 脚本 | 覆盖 | 需求 |
| --- | --- | --- |
| `MainPathSmoke` | 注册 → 登录 → 加地址 → 加购物车 → 下单 → 支付 → 后台发货 → 确认收货（**全走网关**） | REQ-101、601、701、801、708 |
| `ChecklistSmoke` | 手工冒烟清单走查（44 项）：核心主流程 + 登录/权限 + 表单校验 + 错误场景友好提示 | 文档 7.4 清单 |
| `SearchSmoke` | 商品变更消息 → 索引同步 → 关键词命中 → 下架不可见 → 全量重建 | REQ-301、302 |
| `OrderSmoke` | 库存不足不建单、下单锁库存、并发重复提交（Seata 回滚失败方的锁）、取消释放 | REQ-701、703、705 |
| `PaySmoke` | 发起支付、伪造回调 60004、模拟支付、重复支付不重复扣减、查询 | REQ-801 ~ 803 |
| `SeckillSmoke` | 建活动划拨、Redis 预扣、异步落单、售罄、超限购、取消回补后可再抢 | REQ-901 ~ 905 |

脚本都是**单文件 Java**（JDK 21 单文件源码启动，无额外依赖，只需 mysql-connector 在 classpath），
直连本机 MySQL 造数据、通过 HTTP 断言业务结果；服务日志在 `%TEMP%\baiyishop-smoke\`。

## 与单元/集成测试的分工

- `gradlew test`：模块内测试（Mockito 替身 + 真实中间件），202 个用例（32 个测试类），覆盖业务规则与边界
- `scripts/smoke-all.ps1`：跨服务端到端，验证「真实链路 + 真实中间件」下的主路径与关键异常路径

## 性能压测（NFR-01）

```powershell
# 前置：服务已在运行（scripts/smoke-all.ps1 -SkipBuild -KeepRunning -Only None）
powershell -ExecutionPolicy Bypass -File scripts/run-load-test.ps1

# 只想重跑某一轮
powershell ... -SkipPrepare                       # 复用已造的压测数据
powershell ... -SkipBaseline                      # 只跑秒杀那一轮
powershell ... -OnlyPrepare                       # 只造数据（1000 用户 + 令牌 + 秒杀活动）
```

| 文件 | 作用 |
| --- | --- |
| `jmeter/PrepareLoadTest.java` | 造 1000 个用户+地址、走真实登录拿 1000 个令牌、建秒杀活动（划拨 1000）、导出基线商品 id |
| `jmeter/api-baseline.jmx` | 基线计划：200 并发 60s，混合只读接口（商品详情/搜索/首页/分类树），断言业务码 0 |
| `jmeter/seckill-buy.jmx` | 秒杀计划：1000 并发抢购，参数化用户令牌与活动 SKU，提取业务码到 JTL |
| `jmeter/VerifyLoadTest.java` | 压测后核对：秒杀池 sold 是否等于 SUCCESS 记录数（不超卖判定） |
| `run-load-test.ps1` | 编排上述步骤，产出 JTL + JMeter HTML 报告到 `docs/load-test/` |

JMeter 需要单独安装（默认路径 `D:\tools_app\apache-jmeter-5.6.3`，用 `-JmeterHome` 覆盖）。

## 前端联调（原型页面 → 真实网关）

`prototype/` 下的 30 个页面已**全部接入真实接口**（不再使用 mock 数据），通过 Vite 开发服务器的
`/api` 代理访问网关，因此本地没有跨域问题、后端也不用开 CORS。

```powershell
# 1) 起后端：中间件已在跑时用 -SkipMiddleware；跑完保持运行
powershell -ExecutionPolicy Bypass -File scripts/smoke-all.ps1 -SkipBuild -SkipMiddleware -Only None -KeepRunning

# 2) 起前端（首次需要 npm install）
cd prototype
npm install
npm run dev            # http://localhost:5173
```

| 入口 | 地址 | 说明 |
| --- | --- | --- |
| 用户端 Web | http://localhost:5173/index.html | `buyer01 / Demo@2026`（也可自行注册，注册即登录） |
| 运营后台 | http://localhost:5173/admin/login.html | 超管 `demo_admin`、运营 `demo_operator`、客服 `demo_service`，密码均 `Admin@2026` |
| 小程序原型 | http://localhost:5173/miniapp/index.html | 点「我的 → 微信授权登录」走 `MockWechatAuthClient`，code 即 openid |

要点：

- 前台与后台**分开存会话**（`baiyi.*` 与 `baiyi.admin.*`），同一个浏览器可同时登录买家与管理员。
- 接口金额一律为「分」，展示换算在前端（`Store.money()`）；图片地址为 MinIO 示例地址时会回退到本地占位图。
- 首页内容是后台「首页配置」里维护的：刚清过库时首页为空态，先在后台加轮播 / 金刚区 / 楼层。
- 生产/同域部署时把 `window.BAIYI_API_BASE` 设为网关地址，或反代 `/api` 到网关即可（无需改代码）。

## 演示数据（清库 + 播种）

冒烟与压测会在库里留下大量测试数据（随机名分类 / 商品、1000 个压测用户、几十个测试管理员…）。
要还原成一份可演示的数据，跑：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/reset-demo-data.ps1

# 只播种不清库 / 服务没起时只做 SQL 部分
powershell ... -File scripts/reset-demo-data.ps1 -SkipReset
powershell ... -File scripts/reset-demo-data.ps1 -SkipHttp
```

| 步骤 | 内容 |
| --- | --- |
| 清理 | 清空 6 个业务库的分类 / 品牌 / 商品 / 库存（含秒杀池与流水）/ 订单 / 支付 / 秒杀活动 / 用户与地址 / 管理员；**保留** RBAC 字典（role / permission / role_permission） |
| 播种（SQL） | 三级分类（8 + 21 + 28）、5 个品牌、4 个参数模板、42 个商品（各 1 个默认 SKU + 图集 + 参数值）、42 条库存（含 3 条低库存预警）、首页轮播 / 公告 / 金刚区 / 4 个楼层 |
| 播种（接口） | 重建 ES 索引；注册演示买家并下 3 笔不同状态的订单（待付款 / 待发货 / 待收货，其中一笔走后台真实发货）；建 2 个秒杀场次（划拨库存由 seckill → inventory 服务端完成） |

脚本是 `scripts/demo/DemoData.java`（单文件 Java，JDBC + HTTP，无额外依赖），可重复执行：每次都先清后建。
运维三件套（冒烟、压测、演示数据）都在 `scripts/` 下，互不影响；跑完冒烟想让界面恢复干净，再跑一次本脚本即可。
注意：压测器与被测服务在同一台机器时会互相抢资源，容量结论必须在环境分离后重测。
