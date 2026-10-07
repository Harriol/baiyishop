# baiyishop-product · 商品服务

商品中心的**唯一数据源**：三级分类、品牌、商品与 SKU、参数模板与参数值、首页配置（轮播 / 公告 / 金刚区 / 楼层），
并在商品变更时发布领域事件驱动搜索索引同步。

| 项 | 值 |
| --- | --- |
| 端口 | `8082` |
| schema | `baiyishop_product`（账号 `baiyi_product`，13 张表） |
| 依赖 | MySQL、Nacos、RocketMQ（生产商品变更事件，本地消息表投递） |
| 启动类 | `ProductApplication` |

## 对外接口

### 前台只读（公开）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/categories/tree` | 三级分类树（仅可见分类） |
| GET | `/api/v1/categories/{id}/products` | 分类商品列表（**含全部子分类**）；`sort` = `sales`/`new`/`price_asc`/`price_desc`，分页 |
| GET | `/api/v1/brands` | 启用中的品牌列表 |
| GET | `/api/v1/products/{id}` | 商品详情：图集、SKU、价格、参数、富文本详情 |
| GET | `/api/v1/home` | 首页聚合：轮播、公告、金刚区、楼层（楼层商品由服务端按各楼层配置的排序维度拉取） |

### 后台（`/api/v1/admin/**`，`SUPER_ADMIN` / `OPERATOR`）

| 模块 | 接口 |
| --- | --- |
| 分类 | `GET /admin/categories`（含隐藏）、`POST`、`PUT /{id}`、`DELETE /{id}`、`PUT /{id}/visible?visible=` |
| 品牌 | `GET /admin/brands`（分页 + 关键词 + 启用状态）、`POST`、`PUT /{id}`、`DELETE /{id}`、`PUT /{id}/enabled?enabled=` |
| 商品 | `GET /admin/products`（分页 + 关键词 + 分类 + 品牌 + 状态）、`GET /{id}`、`POST`、`PUT /{id}`、`PUT /{id}/on-sale`、`PUT /{id}/off-sale`、`DELETE /{id}`（逻辑删除） |
| 参数 | `/admin/params/templates`（增删改查）、`/admin/params/items`（增删改） |
| 首页配置 | `/admin/home/banners`、`/notices`、`/navs`、`/floors`（各为 `GET` 查询 + `PUT` **整份保存**） |
| 图片上传 | `POST /admin/uploads/images`（multipart：`file` + `scene`），返回 `{url, objectName, size, contentType}` |

> 商品列表按分类筛选是**含子分类**语义：选一级分类也能查到挂在叶子分类下的商品。

## 内部接口 `/internal/products`（不对外暴露）

| 方法 | 路径 | 调用方 | 说明 |
| --- | --- | --- | --- |
| GET | `/{id}/index-doc` | search | 单个商品的索引文档（增量同步） |
| GET | `/index-docs` | search | 索引文档分页（全量重建时逐页拉取） |
| GET | `/{id}` | order / inventory | 商品快照（下单前校验存在性与状态） |
| GET | `/skus/{skuId}` | order / inventory | 单个 SKU 快照 |
| GET | `/skus` | order | 批量 SKU 快照（购物车 / 结算，一次取回避免 N+1） |

## 数据模型

| 表 | 说明 |
| --- | --- |
| `category` | 分类，最多 3 级；`path` 为物化路径（如 `/1/12/135/`），「含子分类」查询按前缀匹配 |
| `brand` | 品牌 |
| `product` | 商品 SPU：名称、分类、品牌、主图、详情、上下架、最低价与销量冗余、上架时间（供排序与搜索新鲜度打分） |
| `product_sku` | SKU；本期单规格但保留结构，编码 `{productId}-{两位序号}` 由服务端生成 |
| `product_image` | 商品图集（首图与主图一致） |
| `param_template` / `param_item` | 参数模板与参数项（后台统一维护） |
| `product_param_value` | 商品参数值（每个商品一组 `param_item` 取值） |
| `home_banner` / `home_notice` / `home_nav` | 首页轮播 / 公告 / 金刚区 |
| `home_floor` / `home_floor_item` | 首页楼层配置与人工置顶商品 |
| `mq_outbox` | 本地消息表（商品变更事件的可靠投递） |

## 图片上传（MinIO）

`POST /api/v1/admin/uploads/images`（`SUPER_ADMIN` / `OPERATOR`，multipart 表单）：

| 字段 | 说明 |
| --- | --- |
| `file` | 图片文件；只接受 JPG / PNG / WebP / GIF，≤5MB（否则 10007 / 10008） |
| `scene` | 业务场景，决定桶内目录：`product`（默认，商品图）/ `banner`（首页轮播） |

对象名按 `{scene}/{yyyyMMdd}/{uuid}.{ext}` 生成，避免同名覆盖；桶不存在时**自动创建并设置为匿名只读**
（图片要被前台 `<img>` 直接加载）。上传失败统一返回 10009，凭据不匹配时会附带明确的排查提示。

## 关键业务规则

- **分类**：最多 3 级；调整父级时自动重算自身与全部后代的 `path`、`level`；**有子分类或被商品引用时禁止删除**
- **商品**：必须挂在**叶子分类**下；删除是逻辑删除（`deleted=1`），与「下架」语义分离，历史订单仍展示商品快照
- **参数**：参数项必须属于所选模板（跨模板取值直接拒绝）；已被商品使用的参数项不可删除
- **首页楼层**：楼层按绑定的分类自动拉商品（含子分类），每个楼层可**独立配置排序维度与展示数量**，
  并支持 `pinnedProductIds` 人工置顶；配置为空时前台展示空态

## 消息

| 方向 | Topic | 说明 |
| --- | --- | --- |
| 生产 | `baiyishop-product-changed` | 商品新增 / 修改 / 上下架 / 删除后的事件，由 `OutboxDispatcher` 定时（默认 2s）从 `mq_outbox` 投递，失败重试上限 10 次后置 `FAILED` 等人工重放 |

## 关键配置

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `baiyishop.mq.product-changed-topic` | `baiyishop-product-changed` | 商品变更事件 topic |
| `baiyishop.mq.outbox-poll-interval-ms` | `2000` | 本地消息表扫描间隔 |
| `baiyishop.mq.outbox-max-retry` | `10` | 投递最大重试次数 |

## 测试

9 个测试类 / 61 个用例：分类树与路径重算、删除约束、品牌引用约束、商品 CRUD 与叶子分类校验、
列表按分类含子分类筛选、参数模板匹配、首页配置保存与楼层取数、上下架与逻辑删除、
图片上传（类型/空文件校验、角色校验、上传后地址可访问）、索引文档内容与本地消息表投递。

## 本地启动

```powershell
.\gradlew.bat :baiyishop-product:bootRun
```
