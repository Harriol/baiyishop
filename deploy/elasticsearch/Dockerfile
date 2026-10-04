# 百益商城 · 带中文分词的 Elasticsearch 镜像
#
# 为什么需要自建镜像：
#   官方镜像不含任何中文分词器，不装的话「耳机」会被当成一个整词，
#   只有标题里连着出现才命中，中文检索基本不可用（REQ-301）。
#
# 为什么选 smartcn 而不是 ik：
#   ik 已停止在 GitHub Releases 发布安装包（仓库 tag 与资源均为空），
#   而本机需要离线/受限网络下可重复构建。analysis-smartcn 是 Elastic 官方中文分词插件，
#   可直接从官方插件仓库下载，构建稳定。
#   两者都只是「分析器名字」的差别：将来若拿到 ik 8.11.3 的 zip，
#   把下面的 install 换成 COPY + file:/// 安装即可，映射里改 analyzer 名字就行。
FROM docker.elastic.co/elasticsearch/elasticsearch:8.11.3

# 插件版本由 ES 镜像自身决定（必须与 ES 版本一致，这里会自动取 8.11.3）
RUN bin/elasticsearch-plugin install --batch analysis-smartcn