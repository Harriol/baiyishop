# baiyishop-search · 搜索服务

商品检索：基于 **Elasticsearch** 的关键词检索 + 多维筛选 + 四种排序，并负责索引的增量同步与全量重建。
**不直连 MySQL**（无 schema），只用 Redis 做消费幂等。

| 项 | 值 |
| --- | --- |
| 端口 | `8083` |
| 数据源 | —（仅 Elasticsearch + Redis） |
| 依赖 | Elasticsearch 8.11.3、Redis（消费幂等）、RocketMQ（消费商品变更）、Nacos |
| 启动类 | `SearchApplication` |

## 对外接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/search/products` | 商品检索（公开） |

查询参数：

| 参数 | 说明 |
| --- | --- |
| `keyword` | 关键词，分词后**要求全部命中**（`operator=and`），避免「纯棉 T 恤」带出其它品类 |
| `categoryId` | 分类筛选；按物化路径展开命中**含子分类** |
| `brandId` | 品牌筛选 |
| `minPrice` / `maxPrice` | 价格区间（单位：分） |
| `sort` | `composite`（默认，综合） / `sales` / `price_asc` / `price_desc`；未知值按综合处理 |
| `page` / `size` | 分页 |

### 综合排序打分

```
score = 销量得分 × 0.6 + 上架新鲜度 × 0.4
销量得分   = min(1, log(1 + sales) / log(1 + 10000))
新鲜度     = clamp(1 - 上架天数 / 30, 0, 1)      // 上架满 30 天后归零
```

权重、归一化基准与新鲜度窗口都可通过配置（Nacos）调整，无需改代码。

## 内部接口 `/internal/search`（不对外暴露）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/reindex?size=200` | **全量重建索引**：建临时索引 → 分页从 product 服务拉全量 → bulk 写入 → 刷新 → **原子切换别名** → 删除旧索引。重建期间查询走旧别名，检索不中断 |

## 索引设计

| 项 | 值 |
| --- | --- |
| 别名 | `baiyishop_product`（查询与写入都走别名） |
| 物理索引 | `baiyishop_product_{yyyyMMddHHmmss}_{随机 4 位}`（每次重建一个新索引，避免同秒撞名） |
| 文档字段 | 商品 id、名称、主图、价格、销量、分类 id 与分类 id 数组（含子分类）、品牌、状态、上架时间 |
| 只索引 | `status = ON_SALE` 的商品（下架即从检索结果中消失） |

## 索引同步

| 方向 | Topic / 组 | 说明 |
| --- | --- | --- |
| 消费 | `baiyishop-product-changed`（组 `baiyishop-search`） | 商品变更事件 → 拉取最新索引文档写入 ES（商品已删除则移除文档） |
| 幂等 | Redis `baiyishop:search:consumed:*` | 以消息键做消费去重，TTL 默认 7 天 |

> 直接改数据库（例如跑数据脚本）不会产生事件，需要手动触发一次 `/internal/search/reindex`。
> `scripts/reset-demo-data.ps1` 已包含重建步骤。

## 关键配置

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `baiyishop.search.elasticsearch-uri` | `http://localhost:9200` | ES 地址 |
| `baiyishop.search.index-alias` | `baiyishop_product` | 索引别名 |
| `baiyishop.search.sales-weight` / `freshness-weight` | `0.6` / `0.4` | 综合得分权重 |
| `baiyishop.search.sales-normalize-max` | `10000` | 销量归一化基准 |
| `baiyishop.search.freshness-days` | `30` | 新鲜度衰减天数 |
| `baiyishop.search.consume-dedup-ttl-days` | `7` | 消费幂等键保留天数 |

> ES 客户端版本被**锁定为 8.11.3**（与服务器一致）：Spring Boot 4 默认管理的 9.x 客户端会发送
> `compatible-with=9` 请求头，8.x 服务器会直接返回 400。

## 测试

4 个测试类 / 15 个用例：关键词命中与全命中语义、分类含子分类、品牌与价格区间筛选、四种排序、
增量同步与删除、全量重建与别名切换、消费幂等。

## 本地启动

```powershell
.\gradlew.bat :baiyishop-search:bootRun
# 需要：Elasticsearch + Redis + Nacos，且 product 服务已启动（重建索引时要用）
```
