package com.bn.aliagent.evaluation.anonymization;

import java.time.Instant;

public record DatasetShareAuthorization(boolean active, String authorizedBy, Instant authorizedAt) {
    public static DatasetShareAuthorization active(String authorizedBy, Instant authorizedAt) { return new DatasetShareAuthorization(true, authorizedBy, authorizedAt); }
    public static DatasetShareAuthorization inactive() { return new DatasetShareAuthorization(false, "", null); }
}
