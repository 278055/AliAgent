# P3-A：知识数据模型、摄入与存储任务上下文

## 任务身份

- 工作目录：`D:\Java\code\AliAgent-worktrees\p3-knowledge-ingestion`
- 工作分支：`codex/p3-knowledge-ingestion`
- 阶段集成分支：`codex/integration-p3`（创建自 `origin/master` 的 `8d8a8ef`）
- 前置条件：开始前确认 P2 已合入目标远端 `master`；若仍未合入，不得假设 `mall`、JWT、内部 API 或 Outbox 存在。

## 目标

在 `knowledge_db` 建立可从空库执行的知识数据底座：文件保存在 MinIO，数据库只保存对象键和元数据；以异步摄入任务完成解析、切片、1024 维向量化和状态迁移。为 P3-B 发布稳定的检索契约与表模型，但不实现在线检索，也不改旧单体。

## 唯一目录所有权

本任务是以下公共位置的唯一修改者：

- `pom.xml`
- `contracts/**`
- `deploy/**`
- `services/knowledge-service/pom.xml`
- `services/knowledge-service/src/main/resources/**`
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/catalog/**`\n- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/api/**`
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/ingestion/**`
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/storage/**`

禁止修改：`src/**` 旧单体、`mall/**`、`frontend/**`、`services/knowledge-service/**/retrieval/**`。不得删除旧 RAG 接口或修改既有 Flyway 脚本。

## 实施边界

1. 在新 Flyway 迁移中建立文档、知识版本、切片、摄入任务和消费幂等记录。所有业务表均有 `tenant_id`，切片保存 `embedding vector(1024)`，并为后续关键词与向量检索建立必要索引。
2. 版本状态至少支持 `DRAFT`、`PROCESSING`、`READY_FOR_REVIEW`、`PUBLISHED`、`RETIRED`；在线可见性由版本状态控制，不能用客户端参数绕过。
3. MinIO 对象键固定以 `knowledge/tenant-{tenantId}/` 开头。持久化对象键、原文件名、媒体类型、长度、校验和和审计元数据，不在数据库保存原始文件内容。
4. `knowledge-api` 接收上传并创建摄入任务；`knowledge-worker` 消费 RabbitMQ 任务，以幂等方式执行 Tika 解析、TokenTextSplitter、DashScope Embedding 和切片写入。失败必须记录可诊断状态，不得留下被错误标记为可发布的版本。
5. 建立上传、任务状态、审核/发布和检索请求/响应的 OpenAPI；事件和任务消息遵守 `eventId`、`eventVersion`、`tenantId`、`traceId` 信封。P3-B 只能实现本任务发布的检索契约，不修改 `contracts/**`。
6. 在根 `pom.xml` 统一加入 P3 所需依赖版本，包含 MinIO、RabbitMQ、Tika、Spring AI Embedding 和 pgvector 所需库；P3-B/C 如需额外依赖，只能提出集成请求。

## 强制安全与测试

- 仅使用 Gateway 注入的可信上下文；后台任务必须显式携带并恢复 `tenantId`。
- 上传和状态查询按租户和权限校验；对象键和日志不得泄露凭据。
- 单测覆盖：对象键租户隔离、重复任务幂等、失败状态、1024 维度拒绝、未发布版本不可被后续检索查询选中。
- 集成测试使用 `test-p3a-<时间戳>` 前缀并在测试结束后清理。数据库结构盘点只能用只读 SELECT；当前 PostgreSQL MCP 返回 `-32603`，先恢复其可用性后再执行实际数据库验证。

## 交付与集成请求

- 先在本 Worktree 验证分支与状态，再实施并提交原子提交。
- 交付提交 SHA、修改文件、Flyway 空库验证、消息幂等和 MinIO 验证结果。
- 请求集成会话先合并 P3-A；P3-B 以合并后的集成分支为起点，P3-C 最后接入。
