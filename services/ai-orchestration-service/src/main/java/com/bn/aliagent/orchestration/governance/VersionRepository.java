package com.bn.aliagent.orchestration.governance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VersionRepository {
    List<ManagedVersion> published(VersionType type);
    List<TenantVersionAssignment> assignments(String tenantId, VersionType type);
    void save(ManagedVersion version);
    void assign(TenantVersionAssignment assignment);
    void rollback(VersionType type, UUID versionId);
    Optional<ExecutionVersionSet> pinned(String executionId);
    void pin(String executionId, ExecutionVersionSet versions);
}
