# P4-C 实时协同与切换上下文

## 固定工作区

- Worktree：`D:\Java\code\AliAgent-worktrees\p4-conversation-realtime`
- 分支：`codex/p4-conversation-realtime`
- 基线：`codex/integration-p4`，启动时为 `fb4ee26`。开始前核对目录、分支和空工作区。

## 任务目标

实现最小人工客服 WebSocket 消息/状态、连接实例路由、Redis Pub/Sub 实例定向通知，以及旧单体聊天与旧前端向新会话服务的功能开关切换适配。P4-C 不实现完整技能组分配、客服副驾、审批或 P5 模型调用；这些属于 P6/P7。

## 唯一文件所有权

允许修改：

- `services/conversation-service/src/main/resources/db/migration/V4__realtime_collaboration.sql`。
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/realtime/**`。
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/config/RealtimeWebSocketConfiguration.java` 与 `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/RealtimeWebSocketController.java`。
- `services/conversation-service/src/test/java/com/bn/aliagent/conversation/realtime/**` 与 `services/conversation-service/src/test/java/com/bn/aliagent/conversation/api/RealtimeWebSocketControllerTest.java`。
- `frontend/src/**`（P4 内唯一前端负责人）。
- `src/main/java/com/bn/aliagent/Controller/ChatController.java`、新增 `src/main/java/com/bn/aliagent/conversationcompat/**`、`src/main/resources/application.yaml`（P4 内唯一旧单体聊天适配和功能开关负责人）。

禁止修改：

- A 的 `core/**`、REST Controller、服务 POM、`application*.yml`、V2、REST/AIReplyRequested 契约。
- B 的 `streaming/**`、`SseStreamController.java`、`StreamingConfiguration.java`、V3、SSE 契约。
- `contracts/**`。C 只能以本文件的“WebSocket 契约请求”向 A 提交补丁请求；A 是 WebSocket 契约的唯一落盘负责人。
- 根 `pom.xml`、`deploy/**`、`platform/**`、`mall/**`、其他服务。缺少依赖/配置时只能向 A 或集成会话提出精确文件请求。

## 已有事实与必须复用的 P3 契约

- P3 的 `knowledge-service` 已通过服务 JWT、可信头、JDBC Outbox、RabbitMQ 与结构化日志建立模式；实时连接同样必须从 Gateway 的可信上下文取得 tenantId、主体、角色、requestId、traceId，不得接受前端声称的 tenantId。
- 旧单体 `ChatController` 提供 `/api/chat`、`/api/chat/stream`、`/api/chat/conversations/**`，直接调用旧 `Agent`；Vue 前端的 `frontend/src/utils/api.ts` 和 `stores/index.ts` 直接消费这些接口。切换必须可关闭并回退旧路由，不能删除旧 Agent、旧 controller 路径或旧前端功能。
- 现有开关规范已定义 `feature.conversation.remote-write` 和 `feature.orchestration.remote-reply`，默认关闭、按租户白名单启用。C 负责前者的旧单体适配；不能把开关实现为浏览器端任意开关。

## WebSocket 契约请求（提交给 A，不自行修改 contracts）

- 握手入口：`GET /api/v1/ws/conversations`，Gateway/服务认证完成后才允许升级。协议采用 JSON 信封，不使用 SSE 承载人工事件。
- 客户端到服务端：`human.send`（`conversationId`、`clientMessageId`、`content`）、`human.takeover`、`human.release`、`agent.presence`；主体必须为 STAFF 且有相应权限。租户、staffId 只取可信上下文。
- 服务端到客户端：`human.message`、`conversation.status`、`conversation.queue`、`agent.presence`、`error`。每条含 `tenantId`、`conversationId`、`requestId`、`occurredAt`，消息类另含持久化 `messageId`、`sequence`、`sender`、`content`。
- P4 最小状态仅 `ONLINE`、`OFFLINE`、`BUSY` 与会话 `WAITING_HUMAN`、`HUMAN_ACTIVE`、`AI_ACTIVE`；不做技能组、容量算法、自动分配或 AI 副驾。
- A 将其写入 `conversation-websocket-v1` 契约后，C 才能把字段视为对外稳定。任何变化须先给 A 书面补丁请求。

## 数据、Redis 与接口边界

- V4 只能建立 C 专属的人工状态、连接路由或投递去重表；引用 A 的 conversation/message 主键并带 tenantId，不能重建或修改 A/B 表。
- 人工公开消息必须通过 A 暴露的核心消息写入端口持久化，获取全局 sequence 后才广播；内部备注只能 STAFF 权限写入且不可向 MEMBER 广播。
- Redis 路由键必须包含 tenantId：`conversation:{tenantId}:route:{conversationId}`、`conversation:{tenantId}:connection:{connectionId}`、`conversation:{tenantId}:agent:{staffId}:presence`。值含 instanceId 与 TTL/心跳；Pub/Sub 频道也带 tenantId 与目标 instanceId。
- 多实例流程：连接建立登记路由 → 本实例/其他实例收到定向 Pub/Sub → 目标实例向该 WebSocket 发出消息。Redis 不可用时不得伪造投递成功：持久化人工消息后允许客户端 REST/SSE/历史补拉，记录 `SYSTEM-*` 降级并提示重连。
- 前端适配只经 Gateway 调用新 `/api/v1` REST、SSE、WebSocket；启用 `feature.conversation.remote-write` 的租户走新接口，关闭时继续走旧 `/api/chat/**`。前端不得保存或设置 tenantId/服务 JWT。

## 安全、审计、幂等、错误和降级

- 所有查询、持久化、缓存键、路由、Pub/Sub 事件都带可信 tenantId；会话与 STAFF 数据范围必须在服务端校验。跨租户消息、路由与订阅均拒绝。
- `human.send.clientMessageId` 是人工消息去重键；重连/重发只返回原持久化消息，不产生重复 sequence。不要复用 A/B 的用户 `requestId` 语义替代客服客户端消息 ID。
- 所有入口、Pub/Sub 消费和广播日志包含 traceId、requestId、tenantId、service、eventId（如有）和 instanceId；不可记录 Token、敏感订单或完整认证信息。
- 错误遵守 `AUTH-*`、`TENANT-*`、`CONV-*`、`SYSTEM-*`，响应/事件带 requestId。关闭远程写开关或新服务异常时，保留旧单体聊天 API/前端路径作为可验证回退，不能删除旧实现。

## 验收与测试数据清理

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p4-conversation-realtime
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -pl services/conversation-service -am test
Set-Location frontend
npm run build
Set-Location D:\Java\code\AliAgent-worktrees\p4-conversation-realtime
git diff --check
```

- 必测：两个实例经 Redis Pub/Sub 定向投递；断线重连可按 message sequence 补拉；同一 clientMessageId 不重复写入；非 STAFF/跨租户连接和接管被拒绝；Redis 不可用时消息事实可从 PostgreSQL 查询且旧链路可回退。
- 使用 `test-p4-c-*` 测试会话、消息、人工状态与 Redis 键；结束时先删子表消息/路由状态，再删 conversation，清空对应 Redis 前缀。不得清理其他 P3/P4 会话或共享缓存。

## 依赖与集成顺序

1. 等待 A 的 V2、核心写入端口、服务 POM/配置及 WebSocket 契约落盘；等待 B 的 V3 后复用序号和终态语义。
2. C 只增加 V4、realtime 文件和获授权的前端/旧单体文件；公共文件需要的变更以请求交给唯一负责人。
3. 集成会话按 A → B → C 合并；C 最后合并以在同一集成分支验证新旧开关与前端回退。
