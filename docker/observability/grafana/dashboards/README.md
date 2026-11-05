# 看板目录（docs/18 §3.3）
#
# 此目录由 docker-compose.observability.yml 只读挂载到 /var/lib/grafana/dashboards，
# Grafana 的 dashboard provider 配置放在 ../provisioning/dashboards/ 下。
#
# 当前暂无预置看板（MVP 阶段先靠 Prometheus UI 的即席查询定位问题）。
# 后续补看板时：JSON 文件放本目录，并在 provisioning/dashboards/ 里加 provider yml，
# 重启 Grafana 容器即自动加载，无需手工导入。
