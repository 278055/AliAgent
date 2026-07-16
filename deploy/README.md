# P1 本地基础设施

本目录仅提供 P1 的本地依赖，不包含业务服务、Flyway 迁移或 mall 业务表。开始前复制 `.env.example` 为 `.env`，并替换所有示例口令。

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps
```

| 服务 | 宿主机端口 | 容器端口 | 说明 |
| --- | --- | --- | --- |
| MySQL | 13306 | 3306 | 仅创建空的 `mall_db` |
| PostgreSQL + pgvector | 15432 | 5432 | 创建五个服务专属数据库 |
| Redis | 16379 | 6379 | 密码认证 |
| RabbitMQ AMQP | 15692 | 5672 | 消息连接 |
| RabbitMQ 管理台 | 15672 | 15672 | 管理界面 |
| Nacos | 18848 | 8848 | 注册与配置中心 |
| MinIO API | 19000 | 9000 | S3 兼容对象存储 |
| MinIO Console | 19001 | 9001 | 管理界面 |

PostgreSQL 初始化只在空数据卷首次创建时运行，生成 `conversation_db`、`orchestration_db`、`knowledge_db`、`evaluation_db`、`insight_db`，并分别授予 `conversation_user`、`orchestration_user`、`knowledge_user`、`evaluation_user`、`insight_user` 对其所属数据库的所有权。`knowledge_db` 同时由 PostgreSQL 管理员启用 `vector` 扩展，供 `knowledge_user` 所属数据库使用。

停止服务使用 `docker compose -f deploy/docker-compose.yml --env-file deploy/.env down`。如需重新运行数据库初始化，必须先确认没有需要保留的本地数据，再删除对应的 Docker 卷。

## P3 自动化集成测试

在已安装 Docker Desktop、JDK 17 和 Maven 的 Windows 主机上运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\test-p3-e2e.ps1
```

脚本使用独立 Compose 项目和 `test-p3-e2e-*` 凭据，自动验证五库账号隔离、RabbitMQ 中断后的 outbox 恢复、MinIO 消费期故障、旧 RAG 远程读取与本地回退。无论成功或失败，都会删除 `rag-test-p3-e2e` 测试数据、临时对象和 Compose 卷；可用 `-KeepEnvironment` 只在故障排查时保留环境。
