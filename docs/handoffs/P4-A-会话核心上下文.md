# P4-A 会话核心上下文

## 固定工作区

- Worktree：`D:\Java\code\AliAgent-worktrees\p4-conversation-core`
- 分支：`codex/p4-conversation-core`
- 基线：`codex/integration-p4`，启动时为 `fb4ee26`。
- 开始前必须执行 `git branch --show-current` 与 `git status --short`；路径或分支不符立即停止。

## 任务目标

建立 `conversation-service` 的会话与消息事实模型、数据库迁移、REST 资源 API、旧库数据迁移工具，以及消息与 `AIReplyRequested` Outbox 的同事务写入。P4 不调用模型、不实现 RAG 或 P5 编排；只允许发布任务事件和提供供 P4-B 写入 Mock AI 片段的领域端口。

## 唯一文件所有权

允许修改：

- `services/conversation-service/pom.xml`、`services/conversation-service/src/main/resources/application.yml`、`services/conversation-service/src/main/resources/application-database.yml`；这是 P4 唯一的服务依赖与配置负责人。为 B/C 预留 Redis、AMQP、WebSocket 所需依赖和以 `conversation.*` 为前缀的配置键，但不得在这些文件中实现 B/C 业务。
- `services/conversation-service/src/main/resources/db/migration/V2__conversation_core.sql`；不可修改已发布的 `V1__baseline.sql`。B/C 分别只能新增 `V3__streaming_reliability.sql`、`V4__realtime_collaboration.sql`。
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/core/**`
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/ConversationCommandController.java`
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/ConversationQueryController.java`
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/ConversationApiExceptionHandler.java`
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/config/ConversationCoreConfiguration.java`
- `services/conversation-service/src/test/java/com/bn/aliagent/conversation/core/**`
- `services/conversation-service/src/test/java/com/bn/aliagent/conversation/api/Conversation*Test.java`
- `contracts/openapi/conversation-v1.yaml`、`contracts/asyncapi/ai-reply-requested-v1.yaml`、`contracts/json-schema/ai-reply-requested-v1.schema.json`，以及唯一由 A 创建/维护的 `contracts/asyncapi/conversation-websocket-v1.yaml`、`contracts/json-schema/conversation-websocket-event-v1.schema.json`。WebSocket 内容以 P4-C 的补丁请求为准；C 无权自行编辑 `contracts/`。

禁止修改：

- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/streaming/**`、`services/conversation-service/src/main/java/com/bn/aliagent/conversation/realtime/**`，及它们的测试和迁移文件。
- `frontend/**`、`src/**`、`deploy/**`、根 `pom.xml`、`platform/**`、`mall/**`、`services/knowledge-service/**`。
- 除所列文件外的 `contracts/**`。根 `pom.xml` 与 `deploy/**` 的唯一负责人是 P4 集成会话；前端和旧单体适配唯一负责人是 P4-C。

## 已有事实与必须复用的 P3 契约

- `conversation-service` 当前只有 `HealthController`、`application*.yml` 和 `V1__baseline.sql` 的 `service_health_probe`，默认关闭 DataSource/Flyway；`database` Profile 通过 `CONVERSATION_DB_URL`、`CONVERSATION_DB_USERNAME`、`CONVERSATION_DB_PASSWORD` 开启。
- P3 的 `knowledge-service` 已示范可信请求上下文、JDBC Outbox、调度投递和 RabbitMQ。复用其“本地事实与 Outbox 同一事务、投递成功后标记、失败保留待重试”的语义，不能复制其数据库表名或绕过事务。
- 已有 `AIReplyRequested` 事件信封 v1：必含 `eventId`、`eventType`、`eventVersion=1`、UTC `occurredAt`、`tenantId`、`traceId`、`producer`、`payload`；当前 payload 固定 `conversationId`、`messageId`、`requestId`。保持兼容，生产者为 `conversation-service`。
- Gateway 注入的可信头由 `contracts/standards/identity-and-context.md` 定义。服务不得从请求体或前端推断 tenantId、主体、角色或权限；必须校验服务 JWT 后再消费这些头。

## 数据模型与精确接口边界

在 V2 建立并由 A 独占的最小事实表：

- `conversation`：`id`、`tenant_id`、`owner_subject_id`、`title`、`status`、`pinned`、`deleted_at`、审计时间。状态只限会话域（例如 `AI_ACTIVE`、`WAITING_HUMAN`、`HUMAN_ACTIVE`、`CLOSED`），不保存订单、退款或审批最终事实。
- `message`：`id`、`tenant_id`、`conversation_id`、`sequence`、`sender_type`、`message_type`、`visibility`、`content`、`status`、`request_id`、`metadata`、审计时间。对 `(tenant_id, conversation_id, sequence)` 唯一；对用户提交消息建立以租户、主体、会话、requestId 为范围的唯一约束，以支撑 B 的重复提交验收。
- `conversation_outbox`：事件 ID、租户、聚合 ID、类型、版本、traceId、requestId、规范 payload、发生时间、发布状态/时间和失败诊断。用户消息、会话更新与 Outbox 必须在同一数据库事务中落库。
- 历史数据迁移只允许复制旧 `postgres` 单体 `conversation`、`message` 到 `conversation_db`，使用可重复执行、可审计的迁移命令；不删除、更新或回写旧库。旧库没有 tenantId 时，迁移必须要求显式 `LEGACY_CONVERSATION_DEFAULT_TENANT_ID`，未提供则拒绝执行，禁止静默填充。

REST 由 A 独占实现并先更新 OpenAPI：

- `POST /api/v1/conversations`：创建会话。
- `GET /api/v1/conversations?page=&pageSize=`、`GET /api/v1/conversations/{conversationId}`。
- `PATCH /api/v1/conversations/{conversationId}`（只允许标题、置顶、关闭等会话域字段）与 `DELETE /api/v1/conversations/{conversationId}`（软删除）。
- `GET /api/v1/conversations/{conversationId}/messages?afterSequence=&pageSize=`：按递增序号补拉已持久化消息；`pageSize` 最大 100。
- `POST /api/v1/conversations/{conversationId}/messages`：请求体含 `content`、非空 UUID `requestId`，并强制 `Idempotency-Key` 等于该 requestId。返回同一持久化用户消息和 `accepted` 结果；首次写入在提交事务内新增 `AIReplyRequested` Outbox，重复请求返回原结果且绝不新增消息或 Outbox。

P4-B 只通过 A 暴露的应用端口读取已提交消息、创建/更新 AI 草稿和终态；不得修改 `core/**`。端口必须强制传入可信 tenantId、conversationId、messageId/requestId，不可接受前端提供的 tenantId。

## 安全、错误、降级与审计

- 每次查询和写入都以可信 tenantId 过滤，并验证会话归属和主体权限；跨租户一律拒绝，不能以“未找到”泄露他租户事实。
- 可重试写入必须有 `Idempotency-Key`；错误使用 `AUTH-*`、`TENANT-*`、`CONV-*`、`SYSTEM-*`，响应遵守 `{code,message,requestId}`，成功遵守 `{code:200,message:"",data}`。
- 日志、Outbox 与迁移审计必须包含 `traceId`、`requestId`、`tenantId`、`service=conversation-service`、`eventId`（如适用）；不得记录原始令牌、完整敏感内容或订单事实。
- `feature.conversation.remote-write` 与 `feature.orchestration.remote-reply` 默认关闭。发布失败保持 Outbox 待投递；旧接口和旧 Agent 是回退路径，不可删除。

## 验收与数据清理

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p4-conversation-core
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -pl services/conversation-service -am test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -pl contracts/contract-validator test
git diff --check
```

- 在隔离 `conversation_db` 上验证 V1+V2 可从空库执行，并验证 `conversation_user` 无法访问旧 `postgres` 单体表或其他服务库。
- 端到端资源必须用 `test-p4-a-*` 前缀；测试完成后由创建者清理其 `message`、`conversation`、`conversation_outbox` 数据。先删 message/outbox，再删 conversation；清理操作只可针对测试前缀。
- 数据库只读检查使用 PostgreSQL MCP 的 `SELECT`；任何 DDL/DML 由经授权的实施/集成测试流程执行，不能在协调阶段执行。

## 依赖与集成顺序

1. A 先提交 V2、核心端口、REST 与 AIReplyRequested 契约；通知 B/C 所需的 POM 和配置键已就绪。
2. B 基于 A 的提交（必要时 rebase）接入流式与可靠性；B 仅维护 SSE 契约文件。
3. C 基于 A 的提交实施实时协同和旧链路适配；WebSocket 契约仅以补丁请求交给 A 落盘。
4. 集成会话按 A → B → C 合并，处理公共配置、根 POM 或部署变更请求；任务分支不得直接合入 main。
