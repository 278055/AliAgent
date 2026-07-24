# P6-C 退款补偿上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p6-refund-compensation`
- 工作分支：`codex/p6-refund-compensation`
- 依赖提交：冻结提交由 `codex/integration-p6` 的 `docs(p6)` 提供。
- 允许修改：`mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/{refund,benefit,notification,compensation}/` 与对应测试。
- 禁止修改：`contracts/`、POM、YAML、迁移、A/B 目录、既有 mall 核心类和 P5。

## 真实接口与状态

mall 当前没有第三方退款实现；P6 只实现 `MockRefundAdapter`。它必须支持成功、明确失败、超时、重复请求幂等和成功请求重查返回原退款事实。`UNKNOWN` 不是失败：超时后先 `RefundPort.query(refundRequestId)`，确认未退款前禁止再次退款。订单取消的真实库存/券/积分回滚已存在于 `OmsPortalOrderServiceImpl`，但 C 不能改该类；在新适配目录封装调用。

退款状态和 Saga 步骤严格使用冻结契约。退款成功后，库存、优惠券、积分和通知按步骤独立记录。任一权益回滚失败不得撤销退款，必须发布失败/人工对账事实并进入 `MANUAL_RECONCILIATION`。

## 幂等、事务与安全

`refundRequestId` 唯一并持久化全部渠道事实；同请求总是返回原结果。每项权益回滚有稳定业务键，不得重复增加库存、券或积分。通知仅说明已提交/失败，不伪造送达。审计与日志保留 `traceId`、`requestId`、`tenantId`，不得记录 Token、完整支付信息和地址。C 只消费/产生冻结的端口和事件，公共契约缺口提出集成请求。

## 测试与交付

测试覆盖 Mock 成功、失败、超时转 UNKNOWN、查询后恢复、重复 refund、三类权益各自幂等、退款成功后权益失败进入人工对账、通知失败可重试。运行 Maven、`git diff --check`、Compose 校验；提交 `feat(p6-c): add refund compensation adapters`，报告 SHA、文件、验证、清理和集成请求。

## 必须使用的 Skill

`superpowers:using-git-worktrees`、`superpowers:brainstorming`、`superpowers:test-driven-development`、失败时 `superpowers:systematic-debugging`、提交前 `superpowers:verification-before-completion`；修改前 CodeGraph；查询 Spring/RabbitMQ/Sentinel 用 `context7`。

## 新会话提示词

```text
你负责 P6-C。进入 D:\Java\code\AliAgent-worktrees\p6-refund-compensation，确认 codex/p6-refund-compensation 和干净状态。阅读 AGENTS.md、docs/tasks/p6/P6-阶段任务边界.md、docs/handoffs/P6-C-退款补偿上下文.md、contracts/standards/aftersale-p6.md。只改 mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/{refund,benefit,notification,compensation}/ 和对应测试；禁止改 contracts、POM、YAML、迁移、A/B 目录、既有 mall 核心类和 P5。

先用 CodeGraph 核对订单取消、库存、券、积分和身份影响面。实现 MockRefundAdapter、权益回滚/通知适配、补偿执行器和人工对账处理；绝不接入支付宝、微信或真实支付。RefundPort 必须按 refundRequestId 幂等，支持成功、明确失败和超时；超时写 UNKNOWN，必须 query 确认未退款才能继续。退款成功后权益失败绝不反向退款，写 MANUAL_RECONCILIATION 和审计/事件。每个回滚独立幂等，敏感数据脱敏。TDD，失败先 systematic-debugging；运行 Maven、diff、Compose 校验，清理 test- 资源，提交 feat(p6-c): add refund compensation adapters，并报告 SHA、文件、验证、清理和集成请求。
```
