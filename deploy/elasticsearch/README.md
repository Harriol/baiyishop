# ES 中文分词（analysis-smartcn）

## 结论：不需要你做任何事

原本计划用 **ik**，但排查后发现 ik 已经不在 GitHub Releases 发布安装包了：

```
api.github.com/repos/infinilabs/analysis-ik/releases  → 只有一个 "Latest" 标签，且没有任何资源
api.github.com/repos/infinilabs/analysis-ik/tags      → 只剩 v1.8.1 等早期标签
```

因此改用 **analysis-smartcn** —— Elastic 官方的中文分词插件，可直接从官方插件仓库下载，
构建可重复，也不需要离线包。

## 它是怎么装进去的

`deploy/elasticsearch/Dockerfile` 里一行：

```dockerfile
RUN bin/elasticsearch-plugin install --batch analysis-smartcn
```

`docker-compose.middleware.yml` 的 elasticsearch 服务由 `image:` 改为 `build:`，
构建一次就固化在镜像里，容器重建也不会丢。

## 手动重建（一般不需要）

```bash
cd deploy
docker compose --env-file .env -f docker-compose.middleware.yml build elasticsearch
docker compose --env-file .env -f docker-compose.middleware.yml up -d elasticsearch
```

## 怎么验证装好了

```bash
# 1) 插件列表应出现 analysis-smartcn
curl "http://localhost:9200/_cat/plugins?v"

# 2) 用 smartcn 切一段中文
curl -X POST "http://localhost:9200/_analyze" -H "Content-Type: application/json" \
  -d '{"analyzer":"smartcn","text":"百益商城纯棉圆领T恤"}'
```

能返回多个词元（而不是把整句当一个词）即说明可用。

## 以后想换成 ik

拿到与 ES 版本一致的 `elasticsearch-analysis-ik-8.11.3.zip` 后：

1. 放到本目录
2. Dockerfile 改成：
   ```dockerfile
   COPY elasticsearch-analysis-ik-8.11.3.zip /tmp/ik.zip
   RUN bin/elasticsearch-plugin install --batch file:///tmp/ik.zip && rm -f /tmp/ik.zip
   ```
3. 重建镜像；索引映射里的 `analyzer` 从 `smartcn` 改成 `ik_max_word`，然后触发一次全量重建索引

> 注意：换分词器会让**已有索引的分词结果失效**，必须重建索引（search-service 会提供重建接口）。