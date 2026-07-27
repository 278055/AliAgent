package com.bn.aliagent.evaluation.runner;

import java.time.Instant;

/** 管理员审批绑定租户和清单摘要，撤销或到期后不可恢复执行。 */
public record DashScopeApproval(String tenantId, String manifestDigest, Instant expiresAt, boolean revoked, DashScopeLimits limits) {
    public boolean permits(String tenant, String digest, Instant now) {
        return !revoked && now.isBefore(expiresAt) && tenantId.equals(tenant) && manifestDigest.equals(digest);
    }
}
