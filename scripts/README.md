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
| `SearchSmoke` | 商品变更消息 → 索引同步 → 关键词命中 → 下架不可见 → 全量重建 | REQ-301、302 |
| `OrderSmoke` | 库存不足不建单、下单锁库存、并发重复提交（Seata 回滚失败方的锁）、取消释放 | REQ-701、703、705 |
| `PaySmoke` | 发起支付、伪造回调 60004、模拟支付、重复支付不重复扣减、查询 | REQ-801 ~ 803 |
| `SeckillSmoke` | 建活动划拨、Redis 预扣、异步落单、售罄、超限购、取消回补后可再抢 | REQ-901 ~ 905 |

脚本都是**单文件 Java**（JDK 21 单文件源码启动，无额外依赖，只需 mysql-connector 在 classpath），
直连本机 MySQL 造数据、通过 HTTP 断言业务结果；服务日志在 `%TEMP%\baiyishop-smoke\`。

## 与单元/集成测试的分工

- `gradlew test`：模块内测试（Mockito 替身 + 真实中间件），175 例，覆盖业务规则与边界
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
注意：压测器与被测服务在同一台机器时会互相抢资源，容量结论必须在环境分离后重测。
