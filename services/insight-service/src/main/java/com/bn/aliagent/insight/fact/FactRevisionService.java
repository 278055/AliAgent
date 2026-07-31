package com.bn.aliagent.insight.fact;

import java.time.Clock;
import java.util.UUID;

public final class FactRevisionService {
    private final LateEventPolicy lateEvents;
    private final RecalculationQueue recalculations;
    private final Clock clock;

    public FactRevisionService(LateEventPolicy lateEvents, RecalculationQueue recalculations, Clock clock) {
        this.lateEvents = lateEvents;
        this.recalculations = recalculations;
        this.clock = clock;
    }

    public AnonymizedFact revise(AnonymizedFact original, AnonymizedFact replacement) {
        var revision = new AnonymizedFact(UUID.randomUUID(), replacement.tenantId(), replacement.type(), replacement.occurredAt(),
                replacement.dimensions(), replacement.measures(), replacement.evidenceRefs(), replacement.sourceEventId(),
                original.revision() + 1, original.factId(), replacement.anonymizationRuleVersion());
        LateEventDecision decision = lateEvents.classify(revision.occurredAt(), clock.instant());
        if (decision != LateEventDecision.ON_TIME) {
            recalculations.enqueue(new RecalculationRequest(revision.tenantId(), revision.factId(), revision.occurredAt(), decision));
        }
        return revision;
    }
}
