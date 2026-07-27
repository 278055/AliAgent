package com.bn.aliagent.evaluation.intake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;

public final class EventIntakeService {
    private static final Set<String> SUPPORTED_TYPES = Set.of("conversation.completed", "conversation.feedback.received",
            "conversation.human.requested", "ai.tool.failed", "copilot.suggestion.actioned");
    private final EventInbox inbox;
    private final PayloadAnonymizer anonymizer;
    private final CandidateSink candidates;
    private final Clock clock;

    public EventIntakeService(EventInbox inbox, PayloadAnonymizer anonymizer, CandidateSink candidates, Clock clock) {
        this.inbox = inbox;
        this.anonymizer = anonymizer;
        this.candidates = candidates;
        this.clock = clock;
    }

    public IntakeRecord accept(EvaluationEventEnvelope event, String trustedTenantId) {
        validate(event, trustedTenantId);
        Instant now = clock.instant();
        // Inbox 只记录事件标识的摘要，避免在匿名化前接触或保存原始正文。
        var reserved = inbox.reserve(event, sha256(event.eventId().toString()), now);
        if (reserved.isEmpty()) return inbox.require(event.eventId());
        try {
            AnonymizedPayload sanitized = anonymizer.anonymize(event.payload(), trustedTenantId);
            candidates.accept(new IntakeCandidate(event.eventId(), trustedTenantId, sanitized.status(), sanitized.canonicalJson(), sanitized.ruleVersion(), now));
            inbox.complete(event.eventId(), clock.instant());
            return inbox.require(event.eventId());
        } catch (RuntimeException ex) {
            inbox.fail(event.eventId(), clock.instant());
            throw ex;
        }
    }

    private void validate(EvaluationEventEnvelope event, String trustedTenantId) {
        if (trustedTenantId == null || trustedTenantId.isBlank() || !trustedTenantId.equals(event.tenantId())) {
            throw new SecurityException("事件租户与可信上下文不匹配");
        }
        if (event.eventVersion() != 1 || !SUPPORTED_TYPES.contains(event.eventType())) {
            throw new IllegalArgumentException("不支持的事件类型或版本");
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
