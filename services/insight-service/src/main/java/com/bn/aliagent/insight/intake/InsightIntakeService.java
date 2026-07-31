package com.bn.aliagent.insight.intake;

import com.bn.aliagent.insight.fact.InsightFactSink;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;

public final class InsightIntakeService {
    private final InsightEventPolicy policy;
    private final InsightEventInbox inbox;
    private final InsightFactProjector projector;
    private final InsightFactSink facts;
    private final Clock clock;

    public InsightIntakeService(InsightEventPolicy policy, InsightEventInbox inbox, InsightFactProjector projector,
            InsightFactSink facts, Clock clock) {
        this.policy = policy;
        this.inbox = inbox;
        this.projector = projector;
        this.facts = facts;
        this.clock = clock;
    }

    public InsightIntakeRecord accept(InsightEventEnvelope event, String trustedTenantId) {
        validateTenant(event, trustedTenantId);
        policy.validate(event);
        if (inbox.reserve(event, sha256(event.eventId().toString()), clock.instant()).isEmpty()) {
            return inbox.require(event.tenantId(), event.producer(), event.eventId());
        }
        try {
            facts.append(projector.project(event));
            inbox.complete(event.tenantId(), event.producer(), event.eventId(), clock.instant());
            return inbox.require(event.tenantId(), event.producer(), event.eventId());
        } catch (RuntimeException exception) {
            inbox.fail(event.tenantId(), event.producer(), event.eventId(), clock.instant());
            throw exception;
        }
    }

    private static void validateTenant(InsightEventEnvelope event, String trustedTenantId) {
        if (trustedTenantId == null || trustedTenantId.isBlank() || !trustedTenantId.equals(event.tenantId())) {
            throw new SecurityException("事件租户与可信上下文不匹配");
        }
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }
}
