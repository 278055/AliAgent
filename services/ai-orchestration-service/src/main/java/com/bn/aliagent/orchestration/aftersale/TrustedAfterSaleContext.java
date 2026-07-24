package com.bn.aliagent.orchestration.aftersale;

import java.util.UUID;

/** 仅接受已由上游验证的身份与服务凭证，禁止从模型输出构造。 */
public record TrustedAfterSaleContext(String tenantId, String actorId, String actorType, String traceId,
                                      UUID requestId, UUID authorizationSnapshotId, String serviceJwt) { }
