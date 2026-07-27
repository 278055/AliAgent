package com.bn.aliagent.evaluation.intake;

import java.util.Map;

@FunctionalInterface
public interface PayloadAnonymizer {
    AnonymizedPayload anonymize(Map<String, Object> payload, String tenantId);
}
