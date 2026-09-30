# 本地开发中间件

依据 `docs/architecture.md` 2.4 的部署视图，用一套 Compose 把 6 类中间件跑起来。

## 数据库：默认使用本机原生 MySQL

本机已安装 **原生 MySQL 8.0**（服务名 `MySQL`，监听 3306），项目直接连它，不启动容器版 MySQL。

首次初始化 6 个 schema 与专用账号：

```powershell
cd deploy
.\mysql\init-native.ps1 -RootPassword '你的root密码'
```

脚本是幂等的，只创建/授权本项目的 6 个 schema，不动其他已有数据库。

若确实想用容器版 MySQL（例如换机器、本机没装 MySQL）：

```bash
docker compose --env-file .env -f docker-compose.middleware.yml --profile docker-mysql up -d
```

它会映射到宿主 **3307**（避开原生 MySQL 的 3306），首次启动同样会执行 `mysql/init/` 下的初始化脚本。

## 首次使用

```bash
cd deploy
cp .env.example .env      # 按需修改密码
docker compose --env-file .env -f docker-compose.middleware.yml up -d
```

## 常用命令

| 目的 | 命令 |
| --- | --- |
| 查看状态 | `docker compose --env-file .env -f docker-compose.middleware.yml ps` |
| 只看日志 | `docker compose --env-file .env -f docker-compose.middleware.yml logs -f nacos` |
| 分阶段启动 | `... up -d redis nacos`（内存紧张时先起这两个） |
| 停止（保留数据） | `... down` |
| 停止并删除数据 | `... down -v`（谨慎） |

## 端口与服务

| 服务 | 容器名 | 端口 | 用途 |
| --- | --- | --- | --- |
| MySQL 8.0 | 本机原生（非容器） | **3306** | 项目默认连它。容器版为可选：`baiyishop-mysql`，映射 3307 |
| Redis 7 | `baiyishop-redis` | 6379 | 缓存、秒杀预扣（开启 AOF，ADR-008） |
| Nacos 2.4.3 | `baiyishop-nacos` | 8848 / 9848 | 注册与配置中心（REQ-1002） |
| RocketMQ 5.3.1 | `baiyishop-rocketmq-namesrv` / `-broker` | 9876 / 10911 | 本地消息表投递与延时消息 |
| Elasticsearch 8.11.3 | `baiyishop-elasticsearch` | 9200 | 商品检索（REQ-301） |
| MinIO | `baiyishop-minio` | 9000 / 9001 | 商品图片（R5-Q4），控制台 9001 |

## 数据库账号

无论用原生 MySQL（`init-native.ps1`）还是容器版（首次启动执行 `mysql/init/01-create-schemas-and-users.sh`），
都会创建下列 6 个 schema 及各自的专用账号：

| schema | 账号 |
| --- | --- |
| `baiyishop_user` | `baiyi_user` |
| `baiyishop_product` | `baiyi_product` |
| `baiyishop_inventory` | `baiyi_inventory` |
| `baiyishop_order` | `baiyi_order` |
| `baiyishop_payment` | `baiyi_payment` |
| `baiyishop_seckill` | `baiyi_seckill` |

账号只能访问自己的 schema，这是 ADR-007 的落地方式；表结构由各服务的 Flyway 脚本管理。

## 宿主端口说明

| 中间件 | 容器内端口 | 宿主端口 | 说明 |
| --- | --- | --- | --- |
| MySQL（容器版，可选） | 3306 | **3307** | 仅在 `--profile docker-mysql` 时启动；避开原生 MySQL 的 3306 |
| Redis | 6379 | 6379 | — |
| Nacos | 8848 / 9848 | 8848 / 9848 | — |
| RocketMQ | 9876 / 10911 | 9876 / 10911 | — |
| Elasticsearch | 9200 | 9200 | — |
| MinIO | 9000 / 9001 | 9000 / 9001 | — |

项目默认连接本机原生 MySQL：`jdbc:mysql://localhost:3306/baiyishop_user`。
若启用容器版 MySQL，则端口改为 3307。

## 本地开发的简化项（不要带到线上）

- Nacos 关闭鉴权（`NACOS_AUTH_ENABLE=false`）
- Elasticsearch 关闭 xpack security
- Redis 未设密码
- RocketMQ 开启 `autoCreateTopicEnable`
- 容器均映射到宿主机端口，未做网络隔离

## RocketMQ 卷属主说明

Docker 具名卷默认属主是 root，而 RocketMQ 容器以 `rocketmq`（uid 3000）运行。
若不做处理，broker 写不进 `store` 目录，启动失败后还会被 shutdown 阶段的 NPE 掩盖真实原因。

因此编排里有一个只做 `chown` 就退出的一次性容器 `baiyishop-rocketmq-init`，
`rocketmq-namesrv` 与 `rocketmq-broker` 都通过 `condition: service_completed_successfully` 依赖它。
看到它 `Exited (0)` 是正常的，不是故障。

## 已知事项

- **ES 中文分词插件 ik 尚未安装**：GitHub 下载通道不通，暂用默认分词器。做搜索服务前需补上（离线安装插件包或自建镜像）。
- **Seata 尚未纳入编排**：接入全局事务时再补 `seata-server`。
- 首次启动会执行 `mysql/init/01-create-schemas-and-users.sh`；若需重新初始化，用 `down -v` 删除数据卷后再起。
- 原生 MySQL 初始化已实测：6 个 schema 为 utf8mb4/utf8mb4_0900_ai_ci，每个账号只被授权自己的 schema，
  越权访问其他 schema 返回 1044/1142，且不影响库中已有的其他数据库。
- 内存占用：Docker VM 配额建议不低于 6 GB；若机器紧张，可分阶段启动（先 `mysql redis nacos`）。
