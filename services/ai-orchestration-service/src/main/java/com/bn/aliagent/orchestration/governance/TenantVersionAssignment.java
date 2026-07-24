package com.bn.aliagent.orchestration.governance;

import java.util.UUID;

public record TenantVersionAssignment(String tenantId, VersionType versionType, UUID versionId, int rolloutPercentage,
                                      String status) {
    public TenantVersionAssignment {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) throw new IllegalArgumentException("灰度比例必须在 0 到 100 之间");
    }
}
