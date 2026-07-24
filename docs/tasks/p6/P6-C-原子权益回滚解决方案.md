# P6-C 原子权益回滚解决方案

## 1. 文档目的

本文解决 P6-A 与 P6-C 在退款成功后执行库存、优惠券和积分回滚时的事务与幂等边界问题。

目标是保证：

- mall 始终是退款、订单、权益、Saga 和人工对账的唯一事实来源。
- P6-C 不信任调用方传入的 SKU、数量、优惠券、积分或退款结论。
- Saga 步骤幂等占有、权益业务写入、步骤结果、状态迁移、审计和 Outbox 位于同一 MySQL 本地事务。
- 退款成功是不可撤销事实，权益回滚失败后不得反向退款。
- 并发调用、重复消息和进程重启不得重复增加库存、恢复优惠券或积分。

本文不引入真实支付渠道，不修改 `OmsPortalOrderServiceImpl`，不复制未支付订单取消逻辑。

## 2. 当前问题

当前 `AtomicBenefitRollbackExecutionService` 已经能够占有 Saga 步骤幂等键，但它传给 `BenefitRollbackPort` 的 `BenefitRollbackCommand` 只有：

- `caseId`
- `tenantId`
- `idempotencyKey`

该命令缺少实际权益写入所需的可信事实：

- 库存：订单项、SKU、回滚数量。
- 优惠券：会员优惠券历史记录 ID。
- 积分：会员 ID、订单实际使用积分数。

如果 P6-C 在原子服务之外再次查询这些事实并更新业务表，会产生以下风险：

1. Saga 步骤占有与业务数据更新不在同一事务。
2. 进程在权益写入后、步骤成功落库前崩溃，重试可能重复回滚。
3. 并发调用可能同时读取旧状态并重复增加库存或积分。
4. 调用方可能向 P6-C 传入错误的权益数量或归属信息。

因此，仅增加 `BenefitRollbackExecutionPort` 还不够；必须同时补齐可信事实解析和事务内调用链。

## 3. 推荐架构

职责划分如下：

| 组件 | 职责 |
| --- | --- |
| P6-A `AtomicBenefitRollbackExecutionService` | 校验可信上下文、解析 mall 事实、占有幂等步骤、开启事务、落步骤/状态/审计/Outbox |
| P6-C `BenefitRollbackPort` 实现 | 使用 P6-A 提供的可信命令执行库存、优惠券、积分写入 |
| P6-C `CompensationExecutor` | 按冻结顺序编排退款、三类权益和通知，不直接访问核心业务表 |
| P6-A `AfterSaleJdbcRepository` | 提供带锁的步骤状态和可信权益事实查询 |

Spring 的事务传播覆盖 P6-A 服务对 P6-C Spring Bean 的调用，因此 P6-C Mapper/Service 更新可以加入 P6-A 已开启的同一事务。

```text
P6-C CompensationExecutor
        |
        v
BenefitRollbackExecutionPort.execute(...)
        |
        +-- 校验 STAFF、tenant、case 状态和 REFUND_SUCCEEDED
        +-- 原子占有 Saga 步骤幂等键
        +-- 从 mall 表解析可信权益事实
        +-- 调用 P6-C BenefitRollbackPort
        +-- 写步骤结果、Saga、售后状态、审计和 Outbox
        |
        v
同一 MySQL 事务提交或整体回滚
```

## 4. 公共接口调整

### 4.1 保留原子执行入口

```java
public interface BenefitRollbackExecutionPort {
    StepResult execute(
            TrustedAfterSaleContext context,
            Long caseId,
            String stepType,
            String idempotencyKey
    );
}
```

`stepType` 只允许：

- `STOCK_RESTORED`
- `COUPON_RESTORED`
- `POINTS_RESTORED`

不允许调用方直接提交权益数值。

### 4.2 扩展内部可信命令

建议将 `BenefitRollbackCommand` 改为只能由 mall 原子执行服务构造的不可变对象：

```java
public final class BenefitRollbackCommand {
    private final Long caseId;
    private final String tenantId;
    private final String idempotencyKey;
    private final Long orderId;
    private final Long memberId;
    private final List<StockRollbackItem> stockItems;
    private final Long couponHistoryId;
    private final Integer usedIntegration;
}

public final class StockRollbackItem {
    private final Long orderItemId;
    private final Long productSkuId;
    private final Integer quantity;
}
```

约束：

- 构造器不得暴露给 Controller、消息 payload 映射器或 AI 编排层。
- 所有字段由 P6-A 根据 `tenantId + caseId` 从 mall 数据库解析。
- `stockItems` 必须支持多个售后订单项，不能只读取第一条。
- `couponHistoryId` 是 `sms_coupon_history.id`，不得使用 `oms_order.coupon_id` 代替。
- `usedIntegration` 来自订单事实，不使用调用方参数。

如果不希望扩大现有公共 DTO，也可以新增包内可见的 `TrustedBenefitRollbackFacts`，由 P6-A 通过受控内部端口传给 P6-C。无论采用哪种类型，数据来源和事务语义必须一致。

## 5. 可信事实解析

P6-A 在步骤占有成功后查询以下事实：

### 5.1 售后与订单归属

- `after_sale_case.id = caseId`
- `after_sale_case.tenant_id = context.tenantId`
- 订单必须存在且属于该售后申请。
- 售后状态只允许 `EXECUTING`；人工明确发起的安全重试可允许约定的重试状态。
- 退款步骤必须已为 `SUCCEEDED`，否则禁止执行权益回滚。

### 5.2 库存事实

通过 `after_sale_case_item.order_item_id` 关联 `oms_order_item.id`，读取：

- `order_item_id`
- `product_sku_id`
- 售后批准回滚数量

数量应来自售后项冻结数量；若 P6 当前仅支持整项退款，应校验它不超过订单项购买数量。

### 5.3 优惠券事实

通过订单 ID、会员 ID 和订单号查询实际被该订单使用的 `sms_coupon_history` 记录。没有使用优惠券时返回空事实，该步骤按成功处理，不产生业务更新。

### 5.4 积分事实

读取 `oms_order.use_integration` 和订单会员 ID。`use_integration` 为空或小于等于零时，该步骤按成功处理，不产生业务更新。

## 6. 原子执行流程

`AtomicBenefitRollbackExecutionService.execute(...)` 必须使用 `@Transactional`，并按以下顺序执行：

1. 校验 `TrustedAfterSaleContext` 为可信服务身份。
2. 校验 tenant、case、订单归属和售后状态。
3. 校验 `stepType` 在允许集合中。
4. 校验 `REFUND_SUCCEEDED` 步骤已经成功。
5. 查询当前步骤记录并处理幂等状态。
6. 原子占有步骤，将状态更新为 `PROCESSING`。
7. 从 mall 数据库解析可信权益事实。
8. 构造内部 `BenefitRollbackCommand`。
9. 在当前事务中调用对应 P6-C 适配器。
10. 成功时写步骤 `SUCCEEDED`、Saga 当前步骤和审计。
11. 失败时写步骤失败事实、售后状态、Saga、审计和 Outbox。
12. 提交事务；任何数据库异常导致整笔事务回滚。

伪代码：

```java
@Transactional
public StepResult execute(
        TrustedAfterSaleContext context,
        Long caseId,
        String stepType,
        String idempotencyKey) {

    validateTrustedContext(context);
    validateCaseAndRefundFact(context.tenantId(), caseId);
    validateStepType(stepType);

    SagaStepFact existing = repository.lockStep(caseId, stepType);
    if (existing.isSucceeded()) {
        return existing.toResult();
    }
    if (!existing.matchesIdempotencyKey(idempotencyKey)) {
        throw new IllegalStateException("saga step idempotency conflict");
    }

    repository.claimStep(caseId, stepType, idempotencyKey);
    BenefitRollbackCommand command =
            repository.loadTrustedBenefitCommand(
                    context.tenantId(), caseId, idempotencyKey);

    try {
        StepResult result = invoke(stepType, command);
        if (!"SUCCEEDED".equals(result.status())) {
            return failAfterRefund(context, caseId, stepType, result.detail());
        }
        repository.completeStep(caseId, stepType, idempotencyKey);
        repository.advanceSaga(caseId, stepType);
        repository.auditSuccess(caseId, context, stepType);
        return result;
    } catch (RuntimeException exception) {
        return failAfterRefund(context, caseId, stepType,
                "benefit rollback failed");
    }
}
```

## 7. 步骤幂等状态机

建议步骤状态至少包含：

| 当前状态 | 相同幂等键行为 | 不同幂等键行为 |
| --- | --- | --- |
| 不存在 | 创建并占有为 `PROCESSING` | 不适用 |
| `PENDING` | 原子占有为 `PROCESSING` | 拒绝 |
| `PROCESSING` | 返回处理中，不重复写权益 | 拒绝 |
| `SUCCEEDED` | 返回原成功事实 | 拒绝 |
| `FAILED` | 退款成功后的权益失败不自动重试，进入人工对账 | 拒绝 |

原子 SQL 必须同时限定：

- `caseId`
- `stepType`
- `idempotencyKey`
- 允许的前置状态

不能先 `SELECT` 再无条件 `UPDATE`。并发安全依赖数据库唯一约束和条件更新，而不是进程内 `Set` 或 `Map`。

## 8. 三类 P6-C 权益适配器

### 8.1 库存回滚

对每个 `StockRollbackItem` 执行真实库存增加：

```text
pms_sku_stock.stock = stock + approvedRollbackQuantity
```

要求：

- 不修改 `lock_stock`，因为已付款订单的锁定库存已在支付成功时释放。
- 校验 SKU 存在且更新行数符合预期。
- 多订单项必须全部成功，否则由外层事务整体回滚。
- 不调用 `OmsPortalOrderServiceImpl.cancelOrder()`。

### 8.2 优惠券回滚

使用可信 `couponHistoryId` 恢复会员优惠券历史：

```text
use_status = 0
use_time = NULL
order_id = NULL
order_sn = NULL
```

要求：

- 仅允许恢复确实由当前订单、当前会员使用的记录。
- 没有优惠券时返回 `SUCCEEDED`。
- 已恢复记录在步骤幂等保护下不得再次执行。

### 8.3 积分回滚

按可信 `usedIntegration` 增加会员当前积分：

```text
ums_member.integration = integration + usedIntegration
```

要求：

- 会员 ID 必须来自订单事实。
- 无使用积分时返回 `SUCCEEDED`。
- 更新应使用数据库原子增量 SQL，避免读改写丢失并发更新。
- Saga 步骤幂等保证相同售后步骤只增加一次。

## 9. 失败与人工对账

退款成功后的任一权益步骤失败时：

1. 保留退款 `SUCCEEDED` 事实。
2. 当前权益步骤写 `BENEFIT_ROLLBACK_FAILED` 或等价明确失败状态。
3. 售后申请转为 `MANUAL_RECONCILIATION`。
4. Saga 转为 `MANUAL_RECONCILIATION`。
5. 同一事务中依次写入：
   - `BenefitRollbackFailed`
   - `ManualReconciliationRequired`
6. 写入脱敏审计。
7. 禁止调用退款端口进行第二次退款、撤销退款或反向补偿。

P6-C 调用原子权益端口后，不得再次用通用 `reportStep(FAILED)` 上报该权益失败，否则可能把 `MANUAL_RECONCILIATION` 错误覆盖为 `RETRY_PENDING`。权益步骤事实完全由原子执行端口负责。

通知失败不影响已完成的退款和权益步骤，只把通知步骤置为可重试状态。

## 10. P6-C 补偿编排

`CompensationExecutor` 只按冻结顺序调用端口：

```text
refund
  -> UNKNOWN 时 query
  -> REFUND_SUCCEEDED
  -> STOCK_RESTORED
  -> COUPON_RESTORED
  -> POINTS_RESTORED
  -> NOTIFICATION_SENT
  -> COMPLETED
```

规则：

- `UNKNOWN` 绝不能当作 `FAILED`。
- `UNKNOWN` 必须调用 `RefundPort.query(refundRequestId)`。
- query 为 `PROCESSING` 时停止本次执行，等待后续查询或重试。
- query 确认成功后才进入权益步骤。
- query 明确确认未退款或明确失败后，才由 Saga 决定安全重试。
- 任一权益步骤失败后立即停止后续自动步骤，进入人工对账。
- 权益步骤由 `BenefitRollbackExecutionPort` 自行记录，C 不重复上报。
- 通知步骤由 C 上报，通知提交不等于用户已收到。

## 11. Mock 退款事实

`MockRefundAdapter` 必须以 `refundRequestId` 为唯一渠道幂等键，并持久化或通过受控仓储保存：

- `refundRequestId`
- `caseId`
- `tenantId`
- `BigDecimal amount`
- 请求时间
- `PENDING/PROCESSING/SUCCEEDED/FAILED/UNKNOWN` 状态
- Mock 渠道引用，例如 `mock-ref-<requestId>`
- 失败分类
- 最后查询时间

相同 `refundRequestId` 重复调用总是返回原事实。已经成功的请求不能生成第二笔退款。Mock 引用不得伪装成支付宝、微信或银行卡流水。

## 12. 安全与审计

日志和审计允许记录：

- `traceId`
- `requestId`
- `tenantId`
- `caseId`
- `refundRequestId`
- Saga 步骤
- 结果分类

禁止记录：

- JWT
- 服务令牌
- 支付令牌
- 完整支付资料
- 完整身份快照
- 收货地址
- 未脱敏手机号

异常详情写入步骤和通知结果前必须转换为稳定、脱敏的错误分类，不能直接持久化第三方异常全文。

## 13. TDD 验收清单

### 13.1 P6-A 原子边界

- 相同步骤和幂等键并发执行只有一个调用实际更新权益。
- 已成功步骤重复调用返回原成功结果。
- 不同幂等键尝试占有同一步骤被拒绝。
- 步骤占有后业务写入异常，事务整体回滚或落明确失败事实，不产生重复权益。
- 权益失败依次产生 `BenefitRollbackFailed` 和 `ManualReconciliationRequired`。
- 退款未成功时禁止执行权益步骤。

### 13.2 P6-C 权益适配器

- 单项和多项库存回滚正确。
- 库存回滚不修改 `lock_stock`。
- 优惠券恢复使用状态并清除订单关联。
- 无优惠券时成功且无写入。
- 积分使用数据库原子增量更新。
- 无积分时成功且无写入。
- 任一项异常会被外层事务处理，不覆盖其他步骤历史事实。

### 13.3 退款与编排

- Mock 退款成功、明确失败、超时转 `UNKNOWN`、处理中。
- `UNKNOWN` 后 query 返回处理中或成功。
- 相同 `refundRequestId` 返回原事实。
- 成功请求不产生第二笔退款。
- 金额保持 `BigDecimal` 精度。
- 退款成功后库存、券、积分分别失败均进入人工对账。
- 代码中不存在反向退款调用。
- 通知失败进入可重试状态，重复通知不重复提交。
- 人工对账未解决时不能完成，重复解决命令幂等。

## 14. 实施顺序

建议按以下顺序修改，避免 P6-A/P6-C 再次出现半完成边界：

1. P6-A 为原子执行服务补充可信事实解析和 `REFUND_SUCCEEDED` 前置校验。
2. P6-A 明确步骤锁定、成功回放和冲突语义。
3. 调整或新增内部可信权益命令。
4. P6-C 实现库存、优惠券和积分适配器。
5. P6-A 在原子事务中调用 P6-C 适配器。
6. P6-C 实现 Mock 退款、通知和补偿编排。
7. 补齐人工对账事实查询与解决命令测试。
8. 运行定向测试、完整 reactor 测试和差异检查。

## 15. 验证命令

PowerShell 中 `-D` 参数应使用引号，且必须显式关闭测试跳过：

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p6-refund-compensation\mall

& "D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd" `
  -s "D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml" `
  -pl mall-portal `
  -am `
  "-DskipTests=false" `
  test
```

必须检查输出中实际存在 `Tests run`，仅有 `BUILD SUCCESS` 但显示 `Tests are skipped` 不能视为测试通过。

随后执行：

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p6-refund-compensation
git diff --check
git status --short
```

## 16. 完成判定

只有同时满足以下条件，P6-C 才可以交付：

- P6-A 原子事务内完成可信事实解析、幂等占有和 P6-C 权益调用。
- 三类权益实际写入均有独立、持久化幂等保护。
- 退款成功后的权益失败进入人工对账，并按顺序产生两个冻结事件。
- 补偿编排不存在反向退款路径。
- 定向测试与完整测试实际执行且通过。
- 没有修改真实支付渠道、`OmsPortalOrderServiceImpl` 或其他禁止目录。

