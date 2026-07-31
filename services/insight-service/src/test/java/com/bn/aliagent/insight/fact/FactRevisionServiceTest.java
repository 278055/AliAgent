package com.bn.aliagent.insight.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FactRevisionServiceTest {
    @Test
    void 七天内自动重算超过七天人工复核() {
        var policy = new LateEventPolicy();
        assertEquals(LateEventDecision.AUTOMATIC_RECALCULATION, policy.classify(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z")));
        assertEquals(LateEventDecision.MANUAL_REVIEW, policy.classify(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-08T00:00:01Z")));
    }

    @Test
    void 事实修订保留替代关系且加入自动重算队列() {
        var queue = new Queue();
        var service = new FactRevisionService(new LateEventPolicy(), queue, Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC));
        var original = fact(1, null);

        var revision = service.revise(original, fact(0, null));

        assertEquals(original.factId(), revision.supersedesFactId());
        assertEquals(2, revision.revision());
        assertEquals(LateEventDecision.AUTOMATIC_RECALCULATION, queue.requests.get(0).decision());
    }

    private AnonymizedFact fact(int revision, UUID supersedes) {
        return new AnonymizedFact(UUID.randomUUID(), "test-p9-a", "refund.succeeded", Instant.parse("2026-07-01T00:00:00Z"),
                Map.of(), Map.of(), Map.of(), UUID.randomUUID(), revision, supersedes, "insight-anon-v1");
    }

    private static final class Queue implements RecalculationQueue {
        private final List<RecalculationRequest> requests = new ArrayList<>();
        public void enqueue(RecalculationRequest request) { requests.add(request); }
    }
}
