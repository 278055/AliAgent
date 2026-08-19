package com.bn.aliagent.insight.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InsightRetentionServiceTest {
    @Test
    void 清理超过九十天的事实和主题成员并保留雷达摘要() {
        var target = new Target();
        var service = new InsightRetentionService(new InsightRetentionPolicy(90, 100), target,
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));

        var result = service.purgeExpired("test-p9-a");

        assertEquals(3, result.deletedFacts());
        assertEquals(2, result.deletedTopicMembers());
        assertEquals(Instant.parse("2026-05-02T00:00:00Z"), target.before);
    }

    private static final class Target implements RetentionTarget {
        private Instant before;
        public int deleteExpiredFacts(String tenantId, Instant cutoff, int batchSize) { before = cutoff; return 3; }
        public int deleteExpiredTopicMembers(String tenantId, Instant cutoff, int batchSize) { return 2; }
    }
}
