# P5 AI 编排运行时设计

## 范围

补齐已合并 P5 核心、适配器和治理模块之间的最小运行期连接。不修改公共契约，不启用写工具，不重构既有领域模型。

## 运行期装配

新增 `OrchestrationRuntimeConfiguration`，注册 v2 消费端、事件映射器、执行存储、规则优先路由、编排服务和工作流执行器。数据库 profile 使用 JDBC 存储；默认 profile 使用内存存储，仅用于离线 Mock 验收。应用启动后恢复未完成执行。

## 五类只读工作流

- GENERAL：仅调用 `ChatModelPort`，随后以 `replyMessageId`、`generationId` 和 `requestId` 回写终止流块。
- RAG：仅调用 `KnowledgeRetrievalPort`；没有 citation 时安全转人工；检索成功时把 citation 随终止流块回写。
- ORDER_QUERY：仅调用 `MallReadToolPort.readOrder`；禁止请求模型生成订单事实。
- LOGISTICS_QUERY：仅调用 `MallReadToolPort.readLogistics`；禁止请求模型生成物流事实。
- HUMAN_HANDOFF：不调用模型和工具，只回写固定的转人工提示。

从输入中提取连续数字作为订单号；没有有效订单号时安全转人工。既有规则会将退款、取消、售后和审批意图路由到 `HUMAN_HANDOFF`。

## 安全与故障边界

执行上下文只来自既有可信事件字段。现有适配器继续传递服务 JWT、授权快照、租户和请求头。模型、知识或 mall 调用的适配器异常不得生成政策或业务事实，统一回写安全转人工提示。只有 `provider=dashscope` 且 API 密钥非空时才创建 DashScope 适配器；其余配置使用 Mock。

## 验证

测试先行覆盖 Spring Bean 装配、五类工作流、重复事件和请求、RAG citation、mall 工具隔离、写意图拦截、流标识符、安全转人工和启动恢复。随后执行模块与完整 Maven 测试、契约校验、Compose 配置校验、空库 Flyway 迁移及可行的离线 Mock 多服务端到端测试。
