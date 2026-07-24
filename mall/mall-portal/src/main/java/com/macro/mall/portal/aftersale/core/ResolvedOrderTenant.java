package com.macro.mall.portal.aftersale.core;

import java.time.Instant;

public final class ResolvedOrderTenant {
    private final Long orderId; private final String tenantId; private final String source; private final Instant resolvedAt;
    public ResolvedOrderTenant(Long orderId, String tenantId, String source, Instant resolvedAt) { this.orderId = orderId; this.tenantId = tenantId; this.source = source; this.resolvedAt = resolvedAt; }
    public Long orderId() { return orderId; } public String tenantId() { return tenantId; } public String source() { return source; } public Instant resolvedAt() { return resolvedAt; }
}
