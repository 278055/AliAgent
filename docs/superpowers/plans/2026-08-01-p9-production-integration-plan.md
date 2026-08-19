# P9 生产事件与外部适配实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 P9 上游 Outbox 生产、Insight MQ 消费、真实外部适配及跨服务验收。

**Architecture:** 生产服务在本地 Outbox 写 v1 事件，dispatcher 经 RabbitMQ 投递。Insight 消费端验证并交由既有 Intake；外部能力仅通过端口适配并在失败时安全降级。

**Tech Stack:** Spring Boot、RabbitMQ、PostgreSQL/pgvector、Spring AI DashScope、JUnit 5。

---

### Task 1: 生产事件公共投递边界

**Files:** 各上游服务 P9 适配包及测试。

- [ ] 先为每个现有 Outbox/dispatcher 写失败测试，证明 P9 信封成功投递才确认发布、失败保留待重试。
- [ ] 以最小映射接入 mall、conversation、orchestration、knowledge 的 P9 v1 信号。
- [ ] 运行相应模块测试并记录结果。

### Task 2: Insight RabbitMQ 消费与故障降级

**Files:** `services/insight-service/src/main/java/com/bn/aliagent/insight/adapter/`、配置和测试。

- [ ] 先写 consumer 转发强类型 Intake 与无效消息拒绝的失败测试。
- [ ] 实现 RabbitMQ listener 和 JSON 信封转换；MQ 缺失时不产生事实。
- [ ] 运行 insight-service 测试。

### Task 3: pgvector、DashScope 与 mall 生产适配

**Files:** Insight adapter、mall 核验 API、测试。

- [ ] 先写端口失败降级与服务 JWT/主管/租户核验的失败测试。
- [ ] 实现 JDBC pgvector 检索、DashScope 命名适配及 mall HTTP 核验端点/客户端。
- [ ] 运行定向测试。

### Task 4: 跨服务 E2E 与清理

**Files:** `tools/` 或专用集成测试、验收报告。

- [ ] 先写可清理 E2E，验证 Outbox→MQ→Inbox→事实→聚合/雷达。
- [ ] 加入 MQ、模型、向量、mall 故障演练并验证没有越权或伪造事实。
- [ ] 清理测试 schema、Redis 键、队列和进程，更新验收报告。

### Task 5: 全量验证与提交

- [ ] 执行 Maven、前端构建、契约校验、Flyway、定向和 E2E 验收、`git diff --check`。
- [ ] 更新报告的证据与阶段出口结论，所有出口均满足后提交 `feat(p9): complete operational insight integration`。
