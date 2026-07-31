package com.bn.aliagent.insight.intake;

import com.bn.aliagent.insight.fact.AnonymizedFact;

@FunctionalInterface
public interface InsightFactProjector {
    AnonymizedFact project(InsightEventEnvelope event);
}
