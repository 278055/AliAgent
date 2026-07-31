package com.bn.aliagent.insight.fact;

import java.time.Duration;
import java.time.Instant;

public final class LateEventPolicy {
    public LateEventDecision classify(Instant occurredAt, Instant receivedAt) {
        Duration delay = Duration.between(occurredAt, receivedAt);
        if (delay.isNegative() || delay.isZero()) return LateEventDecision.ON_TIME;
        return delay.compareTo(Duration.ofDays(7)) <= 0 ? LateEventDecision.AUTOMATIC_RECALCULATION : LateEventDecision.MANUAL_REVIEW;
    }
}
