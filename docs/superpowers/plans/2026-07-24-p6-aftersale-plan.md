# P6 售后闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现可审计、可恢复、仅 Mock 退款的售后写操作闭环。

**Architecture:** A 在 mall portal 独立售后包实现申请、审批、Saga、Outbox/Inbox；B 在 AI 编排独立包实现材料收集、确认卡及命令代理；C 在独立适配包实现 Mock 退款、权益回滚、通知和人工对账。公共契约、迁移、配置由集成会话维护。

**Tech Stack:** Java 17、Spring Boot、MySQL、MyBatis、RabbitMQ、PostgreSQL/Flyway、Maven。

---

### Task 1: P6-A 售后核心

**Files:**
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/api/`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/core/`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/persistence/`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/aftersale/`

- [ ] 写命令幂等、规则版本固定、审批路由、非法迁移、Outbox/Inbox 幂等和本地事务失败不出事件的失败测试。
- [ ] 运行 `mvn -pl mall/mall-portal -Dtest=*AfterSale* test`，确认实现前失败。
- [ ] 实现 `AfterSaleCommandPort`、`AfterSaleQueryPort`、申请/审批状态机、订单取消适配、Saga、审计和 Outbox/Inbox；调用现有 `OmsPortalOrderService.cancelOrder(Long)`，不复制或修改该服务。
- [ ] 运行 `mvn -pl mall/mall-portal test` 与 `git diff --check`，确认通过后提交 `feat(p6-a): add aftersale saga core`。

### Task 2: P6-B AI 售后受控工作流

**Files:**
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/aftersale/`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/confirmation/`
- Test: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/aftersale/`

- [ ] 写售后意图、多轮材料收集、确认前无写、重复确认复用幂等键、可信头透传、mall 故障无事实和人工转接的失败测试。
- [ ] 运行 `mvn -pl services/ai-orchestration-service -Dtest=*AfterSale* test`，确认实现前失败。
- [ ] 实现确认卡、用户确认、受控命令提交、状态查询和事实展示；只使用 mall API/事件事实，保留 P5 已完成只读工作流。
- [ ] 运行 `mvn -pl services/ai-orchestration-service test` 与 `git diff --check`，确认通过后提交 `feat(p6-b): add aftersale workflow`。

### Task 3: P6-C Mock 退款与补偿

**Files:**
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/refund/`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/benefit/`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/notification/`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/compensation/`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/aftersale/`

- [ ] 写 Mock 成功/失败/超时、`UNKNOWN` 后查询、重复退款、各权益幂等、退款后权益失败转人工对账和通知重试的失败测试。
- [ ] 运行 `mvn -pl mall/mall-portal -Dtest=*Refund* test`，确认实现前失败。
- [ ] 实现 `MockRefundAdapter`、`RefundPort.query`、三类权益回滚、通知、补偿执行器和人工对账；禁止真实支付，权益失败后不得撤销已成功退款。
- [ ] 运行 `mvn -pl mall/mall-portal test` 与 `git diff --check`，确认通过后提交 `feat(p6-c): add refund compensation adapters`。

### Task 4: 集成验收

- [ ] 按 A、C、B 顺序合并任务分支；语义冲突退回目录所有者修复。
- [ ] 运行根聚合 Maven 测试、`git diff --check` 和 `docker compose -f deploy\docker-compose.yml config --quiet`。
- [ ] 删除本阶段产生的所有 `test-`/`rag-test-` 数据，报告命令、退出码、清理和剩余风险。
