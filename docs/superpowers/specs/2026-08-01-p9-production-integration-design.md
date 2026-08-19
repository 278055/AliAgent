# P9 生产事件与外部适配设计

## 目标

将 mall、会话、编排和知识服务的 P9 v1 信号以本地事务 Outbox 投递到 `insight.events.v1`，由洞察服务幂等接收；补齐 pgvector、DashScope 和 mall 核验的生产适配，并提供可清理的跨服务验收。

## 架构

各生产服务仅在自己的事务边界写入版本化事件信封。各自的 dispatcher 在投递成功后标记 Outbox，失败保留并退避重试。Insight RabbitMQ consumer 只把已验证信封转交强类型 Intake；MQ 不可用或消息无效时不生成任何替代事实。

主题分析经 `EmbeddingPort`、`ClusteringPort` 与 `TopicNamingPort` 装配：embedding 或 pgvector 失败时保留规则分类结果且不伪造聚类成员；DashScope 失败时主题保持待命名。mall 核验使用服务 JWT、可信身份及租户透传的 HTTP API；失败立即拒绝且不写 `insight_db`。

## 安全与约束

- 事件信封固定包含 eventId、eventType、eventVersion、occurredAt、tenantId、traceId、producer、payload。
- payload 仅携带契约允许的脱敏业务字段，禁止完整订单和个人敏感资料。
- Mall 核验仅允许主管，必须含 radarId、reason、evidenceRef，并同时校验服务 JWT、用户身份、租户和订单归属。
- QueryPlan 仍只可通过既有白名单语义执行；禁止 SQL、库表列、函数、连接和表达式。

## 验收

独立 E2E 使用 `test-` 前缀，覆盖生产者 Outbox、Rabbit 投递、Insight Inbox、匿名事实、聚合和雷达。故障测试分别证明 MQ 不产生替代事实、模型失败待命名、向量失败不改变成员、mall 核验失败拒绝。finally 清理数据库 schema、Redis 键和 Rabbit 队列。
