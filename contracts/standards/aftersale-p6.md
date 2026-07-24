# P6 售后命令与事件契约

## 边界

`mall` 是订单、售后、审批、退款、库存、优惠券、积分、Saga 和审计的唯一事实来源。`ai-orchestration-service` 只识别意图、收集材料、渲染确认卡并提交受控命令；不得直连 mall 数据库，不得把模型输出作为退款、取消或审批事实。

所有写请求使用 `/api/v1`、`Idempotency-Key`，并遵守 [api-and-event.md](api-and-event.md) 与 [identity-and-context.md](identity-and-context.md)。接口和事件的 `tenantId`、主体、权限均来自可信上下文；请求体同名字段只能用于一致性校验，冲突即拒绝。

## 状态机

| 实体 | 状态 |
| --- | --- |
| 售后申请 | `DRAFT`、`WAITING_USER_CONFIRMATION`、`SUBMITTED`、`WAITING_STAFF_APPROVAL`、`WAITING_SUPERVISOR_APPROVAL`、`APPROVED`、`REJECTED`、`EXECUTING`、`RETRY_PENDING`、`COMPLETED`、`FAILED`、`MANUAL_RECONCILIATION`、`CANCELLED` |
| 退款 | `NOT_REQUIRED`、`PENDING`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`UNKNOWN` |
| Saga 步骤 | `AFTERSALE_CREATED`、`ORDER_CANCELLED`、`REFUND_SUCCEEDED`、`STOCK_RESTORED`、`COUPON_RESTORED`、`POINTS_RESTORED`、`NOTIFICATION_SENT`、`COMPLETED`、`MANUAL_RECONCILIATION` |
| 审批 | `USER_CONFIRMATION`、`STAFF_APPROVAL`、`SUPERVISOR_APPROVAL` |

允许迁移：`DRAFT -> WAITING_USER_CONFIRMATION -> SUBMITTED`；未支付取消在用户确认后进入 `EXECUTING`，其余申请按固定规则进入客服或主管审批；批准后 `EXECUTING`。每一步成功才推进；可安全重试的失败进入 `RETRY_PENDING`。退款成功后任一权益回滚失败必须进入 `MANUAL_RECONCILIATION`，严禁反向撤销退款。拒绝、取消和不可恢复失败分别进入终态。

规则在创建申请时解析并持久化 `ruleVersionId`，重试不得重新解析。主管审批条件为金额大于等于 `500.00`、规则标为高风险、重复退款嫌疑、订单主体或租户不匹配；后两项也不得由 AI 自行豁免。

## 端口

| 端口 | 最小职责 | 所有者 |
| --- | --- | --- |
| `AfterSaleCommandPort` | 创建草稿、确认、审批、重试、人工对账决议 | A |
| `AfterSaleQueryPort` | 按申请、订单和幂等键查询事实与状态 | A |
| `RefundPort` | `refund(command)`、`query(refundRequestId)` | C |
| `BenefitRollbackPort` | 库存、优惠券、积分的独立、幂等回滚 | C |
| `NotificationPort` | 发送可重试通知，不伪造送达 | C |
| `AfterSaleSagaRepository` | 申请、Saga、步骤及乐观状态持久化 | A |
| `ApprovalRepository` | 审批记录与授权快照持久化 | A |
| `AfterSaleOutboxPort` / `AfterSaleInboxPort` | 原子事件写入与 `eventId + consumer` 去重 | A |
| `RuleVersionResolver` | 创建时解析、固定并返回规则版本 | A |
| `AfterSaleAuditPort` | 记录命令、状态转换、审批和人工对账审计 | A |

`RefundPort` 的 `refundRequestId` 是渠道幂等键。相同键重复 `refund` 必须返回原退款事实；超时后的本地状态为 `UNKNOWN`，只能先调用 `query`，确认未退款后才能继续操作。默认实现只能是 `MockRefundAdapter`，可显式模拟成功、明确失败、超时和重复请求；P6 禁止接入微信、支付宝或任何真实支付渠道。

## 命令与事件

命令类型为 `AfterSaleDraftCreated`、`AfterSaleConfirmed`、`CancelOrderRequested`、`RefundRequested`、`ReturnRefundRequested`、`StaffApprovalSubmitted`、`SupervisorApprovalSubmitted`、`RetrySagaRequested`、`ManualReconciliationResolved`。每个命令均包含：`commandId`、`commandType`、`commandVersion`、`occurredAt`、`tenantId`、`traceId`、`requestId`、`idempotencyKey`、`actorId`、`actorType`、`authorizationSnapshotId`、`payload`。

`commandId` 全局唯一；`(tenantId, commandType, idempotencyKey)` 唯一。服务端必须校验 actor 对订单和申请的租户、主体及审批权限；不得信任 AI 传入的状态或金额。

事件采用新 Topic：`mall.aftersale.v1`，不修改已有事件载荷。事件类型为 `AfterSaleCreated`、`UserConfirmationRequired`、`AfterSaleSubmitted`、`StaffApprovalRequired`、`SupervisorApprovalRequired`、`AfterSaleApproved`、`AfterSaleRejected`、`OrderCancelled`、`RefundProcessing`、`RefundSucceeded`、`RefundFailed`、`BenefitRollbackFailed`、`ManualReconciliationRequired`、`AfterSaleCompleted`。全部使用既有事件信封，`eventVersion=1`，由本地事务写入 Outbox；消费者以 `(eventId, consumer)` 去重。

## 数据库与迁移

mall 的售后事实属于 MySQL `mall` 库。冻结的表名为 `after_sale_case`、`after_sale_case_item`、`after_sale_approval`、`after_sale_saga`、`after_sale_saga_step`、`refund_record`、`benefit_rollback_record`、`after_sale_outbox`、`after_sale_inbox`、`after_sale_audit`。金额全部使用 `DECIMAL(19,2)`，禁止 `float`/`double`。

唯一约束：`command_id`；`(tenant_id, command_type, idempotency_key)`；`refund_request_id`；有效订单项上的冲突活动申请；`after_sale_outbox.event_id`；`(after_sale_inbox.event_id, consumer_name)`。公共基础迁移位于 `mall/mall-portal/src/main/resources/db/migration/`，并由 portal 的 Flyway 在既有 MySQL `mall` schema 上以 baseline version `0` 执行；实施任务不得私改 POM 或 `application*.yml`。
