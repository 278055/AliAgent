# P6-A mall 售后 Saga 上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p6-mall-aftersale-saga`
- 工作分支：`codex/p6-mall-aftersale-saga`
- 依赖提交：冻结提交由 `codex/integration-p6` 的 `docs(p6)` 提供。
- 允许修改：`mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/{api,core,persistence}/` 与对应测试。
- 禁止修改：`contracts/`、POM、YAML、迁移、C 目录、B 服务、现有 `OmsPortalOrderServiceImpl`、P5 代码。

## 真实接口与状态

复用 `OmsPortalOrderService.cancelOrder(Long)`：仅未支付、未删除订单可取消，现有实现会关单、解锁库存、恢复优惠券及积分。`cancelTimeOutOrder()` 是同一语义的批处理链路。`OmsOrderReturnApplyServiceImpl.updateStatus()` 不是退款通道，不能作为 P6 退款成功依据。订单状态：`0`待付款、`1`待发货、`2`已发货、`3`完成、`4`关闭、`5`无效。

实现 `AfterSaleCommandPort`、`AfterSaleQueryPort`、`AfterSaleSagaRepository`、`ApprovalRepository`、`AfterSaleOutboxPort`、`AfterSaleInboxPort`、`RuleVersionResolver`、`AfterSaleAuditPort` 的 A 侧实现，使用冻结状态机和事件契约。创建时固定规则版本；审批阈值/高风险条件不可在重试时变化。A 不能实现 `RefundPort`、权益回滚、通知或人工对账执行器。

## 幂等、事务与安全

以 `commandId` 及 `(tenantId, commandType, idempotencyKey)` 去重；Outbox 使用稳定 `eventId`、Inbox 使用 `(eventId, consumer)`。申请、命令、Saga 和 Outbox 在同一 MySQL 事务；订单取消适配必须在调用前后验证租户、订单所属会员、状态和申请状态。只信任 Gateway 注入的 MEMBER/STAFF、授权快照、服务 JWT；审计不记录 Token、支付详情或完整地址。

## 降级与测试

安全可重试失败进入 `RETRY_PENDING`；拒绝进入 `REJECTED`；未知退款或退款后权益失败交给 C 的状态回执，A 据此进入 `MANUAL_RECONCILIATION`。写测试覆盖重复命令、非法状态迁移、取消资格、审批路由、规则版本固定、Outbox/Inbox 幂等和事务失败不出事件。

运行 Maven、`git diff --check`、Compose 静态校验；测试数据使用并清理 `test-` 前缀。提交格式 `feat(p6-a): add aftersale saga core`，交付 SHA、文件、命令/结果、清理和集成请求。

## 必须使用的 Skill

`superpowers:using-git-worktrees`、`superpowers:brainstorming`、`superpowers:test-driven-development`、失败时 `superpowers:systematic-debugging`、提交前 `superpowers:verification-before-completion`；修改代码前先 CodeGraph，查询 Spring/RabbitMQ/Sentinel 用 `context7`。

## 新会话提示词

```text
你负责 P6-A。工作目录 D:\Java\code\AliAgent-worktrees\p6-mall-aftersale-saga，分支 codex/p6-mall-aftersale-saga；先确认分支和工作区干净。完整阅读 docs/tasks/p6/P6-阶段任务边界.md、docs/handoffs/P6-A-mall售后Saga上下文.md、contracts/standards/aftersale-p6.md 及 AGENTS.md。只允许修改 mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/{api,core,persistence}/ 和对应测试；禁止修改 contracts、POM、YAML、迁移、其它任务目录、既有 mall 核心类和 P5。

先用 CodeGraph 定位调用方和影响面。实现售后申请、审批、未支付取消、Saga 核心和 Outbox/Inbox；复用 OmsPortalOrderService.cancelOrder(Long) 的既有取消事实，绝不把 OmsOrderReturnApply 的完成状态当退款成功。遵守冻结状态机、命令/事件信封、规则创建时固定、命令和事件幂等；创建申请/Saga/Outbox 同一 MySQL 事务。所有身份和租户来自可信头/授权快照，禁止 AI 或调用方伪造。不要实现 RefundPort、权益回滚、通知、人工对账或真实支付。TDD，失败先 systematic-debugging；运行 Maven、diff、Compose 校验，清理 test- 资源，提交 feat(p6-a): add aftersale saga core，并报告 SHA、文件、验证、清理和集成请求。
```
