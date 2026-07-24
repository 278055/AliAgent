package com.macro.mall.portal.aftersale.api;

/** 仅由已验证的网关注入，禁止从请求 payload 构造。 */
public final class TrustedAfterSaleContext {
    private final String tenantId; private final Long actorId; private final String actorType;
    private final String authorizationSnapshotId; private final String traceId; private final String requestId;
    public TrustedAfterSaleContext(String tenantId, Long actorId, String actorType, String authorizationSnapshotId, String traceId, String requestId) {
        this.tenantId = tenantId; this.actorId = actorId; this.actorType = actorType; this.authorizationSnapshotId = authorizationSnapshotId; this.traceId = traceId; this.requestId = requestId;
    }
    public String tenantId() { return tenantId; } public Long actorId() { return actorId; } public String actorType() { return actorType; }
    public String authorizationSnapshotId() { return authorizationSnapshotId; } public String traceId() { return traceId; } public String requestId() { return requestId; }
    public boolean isMember() { return "MEMBER".equals(actorType); } public boolean isStaff() { return "STAFF".equals(actorType); }
}
