package com.bn.aliagent.evaluation.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateReviewServiceTest {
    private static final String TENANT = "test-p8-a-tenant";
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptingRequiresExpectedResultAndLabels() {
        var candidate = EvaluationCandidate.pending(UUID.randomUUID(), TENANT, "{\"input\":\"test\"}", "digest", clock.instant());
        var service = new CandidateReviewService(new CandidateRepository.InMemory());
        service.save(candidate);

        assertThrows(IllegalArgumentException.class, () -> service.review(new CandidateReviewCommand(candidate.id(), TENANT, "reviewer", ReviewAction.ACCEPT, Map.of(), Set.of(), "ok")));
        var accepted = service.review(new CandidateReviewCommand(candidate.id(), TENANT, "reviewer", ReviewAction.ACCEPT, Map.of("intent", "order.query"), Set.of("test"), "ok"));

        assertEquals(CandidateStatus.ACCEPTED, accepted.status());
        assertEquals("order.query", accepted.expected().get("intent"));
    }

    @Test
    void rejectsCrossTenantReviewAndExpiresOrRedactsCandidates() {
        var repository = new CandidateRepository.InMemory();
        var pending = EvaluationCandidate.pending(UUID.randomUUID(), TENANT, "{\"input\":\"test\"}", "digest", Instant.parse("2026-06-27T00:00:00Z"));
        var rejected = EvaluationCandidate.pending(UUID.randomUUID(), TENANT, "{\"input\":\"test\"}", "digest", Instant.parse("2026-07-20T00:00:00Z"));
        var reviews = new CandidateReviewService(repository);
        reviews.save(pending);
        reviews.save(rejected);
        reviews.review(new CandidateReviewCommand(rejected.id(), TENANT, "reviewer", ReviewAction.REJECT, Map.of(), Set.of(), "not useful"));

        assertThrows(SecurityException.class, () -> reviews.review(new CandidateReviewCommand(pending.id(), "other-tenant", "reviewer", ReviewAction.REJECT, Map.of(), Set.of(), "x")));
        var result = new CandidateRetentionService(repository, clock).purgeExpired();

        assertEquals(1, result.expired());
        assertEquals(1, result.redacted());
        assertEquals(CandidateStatus.EXPIRED, repository.require(TENANT, pending.id()).status());
        assertFalse(repository.require(TENANT, pending.id()).body().isPresent());
        assertFalse(repository.require(TENANT, rejected.id()).body().isPresent());
    }

    @Test
    void quarantinedCandidateCannotBeAccepted() {
        var candidate = EvaluationCandidate.quarantined(UUID.randomUUID(), TENANT, "anonymization failed", clock.instant());
        var service = new CandidateReviewService(new CandidateRepository.InMemory());
        service.save(candidate);

        assertThrows(IllegalStateException.class, () -> service.review(new CandidateReviewCommand(candidate.id(), TENANT, "reviewer", ReviewAction.ACCEPT, Map.of("intent", "x"), Set.of("test"), "x")));
    }
}
