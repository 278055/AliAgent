package com.bn.aliagent.evaluation.anonymization;

import java.util.Map;

public final class PublicDatasetAnonymizer {
    private final DeterministicAnonymizer anonymizer;
    public PublicDatasetAnonymizer(DeterministicAnonymizer anonymizer) { this.anonymizer = anonymizer; }
    public AnonymizationResult anonymize(Map<String, Object> payload, String tenantId, DatasetShareAuthorization authorization) {
        if (authorization == null || !authorization.active() || authorization.authorizedBy().isBlank() || authorization.authorizedAt() == null) {
            throw new SecurityException("公共共享需要有效的显式授权");
        }
        return anonymizer.anonymize(payload, tenantId);
    }
}
