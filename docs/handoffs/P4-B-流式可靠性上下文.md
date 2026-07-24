# P4-B 流式可靠性上下文

## 固定工作区

- Worktree：`D:\Java\code\AliAgent-worktrees\p4-conversation-streaming`
- 分支：`codex/p4-conversation-streaming`
- 基线：`codex/integration-p4`，启动时为 `fb4ee26`。开始前核对分支和空工作区。

## 任务目标

实现 AI 流的 SSE 投递、按消息序号断线补拉、请求幂等行为验收、取消生成、Redis 草稿缓存与重启恢复。P4 只能用内部 Mock/占位流写入端口验证链路；不实现模型调用、P5 编排或 RAG。

## 唯一文件所有权

允许修改：

- `services/conversation-service/src/main/resources/db/migration/V3__streaming_reliability.sql`。
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/streaming/**`。
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/SseStreamController.java` 与 `services/conversation-service/src/main/java/com/bn/aliagent/conversation/config/StreamingConfiguration.java`。
- `services/conversation-service/src/test/java/com/bn/aliagent/conversation/streaming/**` 与 `services/conversation-service/src/test/java/com/bn/aliagent/conversation/api/SseStreamControllerTest.java`。
- `contracts/asyncapi/conversation-sse-v1.yaml`、`contracts/json-schema/conversation-sse-event-v1.schema.json`；B 是 SSE 契约唯一负责人。

禁止修改：

- A 所有 `core/**`、`ConversationCommandController.java`、`ConversationQueryController.java`、服务 POM、`application*.yml`、V2 和 A 的契约文件。
- C 所有 `realtime/**`、`V4__realtime_collaboration.sql`、WebSocket 配置、`frontend/**`、`src/**`。
- 根 `pom.xml`、`deploy/**`、`platform/**`、`mall/**`、其他服务。对缺少依赖或配置键只能向 A/集成会话提出文件级变更请求，不能自行修改公共文件。

## 已有事实与必须复用的 P3 契约

- 当前会话服务仅有 Spring MVC 健康接口；DataSource/Flyway 仅在 `database` Profile 开启。A 将拥有服务 POM 与配置，B 必须等待/合并 A 的依赖准备提交后运行集成测试。
- P3 已验证 Outbox 的“投递成功后标记、失败保留重试”语义。SSE 不是 RabbitMQ 的替代，不得把草稿片段当作跨服务最终事实。
- 可信上下文、服务 JWT、结构化日志、错误包装和 `AIReplyRequested` 事件均遵守 `contracts/standards/`。所有 Redis 键、草稿、SSE 事件与内部流入站都以可信 tenantId 隔离。

## 精确接口、事件和模型边界

- 对外 SSE：`GET /api/v1/conversations/{conversationId}/stream?afterSequence={n}`，只发送该租户可访问会话的 AI 公开消息、工具进度和终态；`afterSequence` 用于最终消息补拉，不能跳过持久化顺序。
- SSE 事件名称固定为 `message.delta`、`message.completed`、`message.interrupted`、`stream.heartbeat`、`error`。`conversation-sse-event-v1` 的公共字段固定为 `tenantId`、`conversationId`、`messageId`、`sequence`、`requestId`、`occurredAt`；`message.delta` 另含 `delta` 与单调 `chunkIndex`，终态另含 `status`。不得在事件中传输令牌、订单或退款事实。
- 内部占位流入站：`POST /internal/api/v1/conversations/{conversationId}/generations/{generationId}/chunks`，仅接受通过服务 JWT 的调用，使用可信头中的 tenantId、traceId、requestId；请求体只允许 `messageId`、`chunkIndex`、`delta`、`final`、`finishReason`。同一 generation/chunkIndex 重放必须幂等。
- 取消：`POST /api/v1/conversations/{conversationId}/generations/{generationId}:cancel`。先持久化取消/中断终态，再写 Redis 取消标记并推送 SSE；后续片段不得复活已取消消息。
- V3 仅可创建 B 专属的流状态/检查点表，例如 generation/checkpoint/consumer-cursor；不得重定义 A 的 `conversation`、`message`、`conversation_outbox`。A 的核心端口负责用户消息及 requestId 的原子持久化；B 负责重试路径、SSE 生命周期和幂等验收用例，不能绕过该端口直接插入用户消息。
- Redis 键一律含 tenantId、conversationId、generationId：`conversation:{tenantId}:{conversationId}:generation:{generationId}:draft`、`:cancelled`、`:checkpoint`。设置 TTL，Redis 故障时降级为 PostgreSQL 已检查点内容和终态补拉，不得丢失最终消息事实。

## tenantId、鉴权、审计、幂等、错误和降级

- 验证服务 JWT 后绑定可信上下文；tenantId 必须同时匹配会话、消息、Redis 键和内部流入口，任何不匹配返回 `TENANT-*`。
- `requestId` 来自 A 的 REST 提交事实；相同 tenantId/主体/会话/requestId 返回原用户消息和原 generation 引用，绝不重复写消息或重复发布 `AIReplyRequested`。B 必须以集成测试证明此行为。
- 生成片段可重复，但按 generationId/chunkIndex 去重；最终态只允许一次。恢复时将无活动上游的 `STREAMING` 标记 `INTERRUPTED` 或从检查点继续，不得假定模型仍在运行。
- 使用 `CONV-*`（已取消、序号非法、流不存在）、`AUTH-*`、`TENANT-*`、`SYSTEM-*`；错误响应带 requestId。日志含 traceId/requestId/tenantId/eventId（如有），禁止记录完整对话正文。
- `feature.orchestration.remote-reply=false` 时只允许 Mock 内部写入或明确中断，不得调用旧 Agent 或模型；旧前端回退不由 B 修改，交给 C。

## 验收与清理

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p4-conversation-streaming
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -pl services/conversation-service -am test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -pl contracts/contract-validator test
git diff --check
```

- 必测：同一 requestId 重放不重复消息/Outbox；SSE 断线后按 sequence 补齐终态；Redis 故障与服务重启后可得到检查点或 `INTERRUPTED`；取消后片段重放不改变终态；跨租户订阅与内部写入均被拒绝。
- 测试资源用 `test-p4-b-*` 前缀，测试结束删除该前缀的 message、流状态、Outbox 和 conversation，先子表后父表；Redis 键必须使用独立前缀并删除。

## 依赖与集成顺序

1. 等待 A 完成 V2、核心端口、服务 POM/配置和 REST 提交语义；必要时 rebase 到 A 提交。
2. B 只增加 V3 与 streaming 文件；若需要 A 的端口扩展，提交精确接口请求，不能修改 A 文件。
3. C 可复用 B 公开终态和序号，不得复用 SSE 作为人工消息通道。
4. 集成顺序固定 A → B → C；B 的契约和测试先在自身分支通过再提交。
