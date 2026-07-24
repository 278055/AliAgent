package com.bn.aliagent.orchestration.aftersale;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 冻结信封字段由可信上下文和确认卡派生，不接收模型声明的结果状态。 */
public record ControlledAfterSaleCommand(UUID commandId, String commandType, int commandVersion, Instant occurredAt,
                                         String tenantId, String traceId, UUID requestId, String idempotencyKey,
                                         String actorId, String actorType, UUID authorizationSnapshotId,
                                         Map<String, String> payload) { }
