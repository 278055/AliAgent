package com.bn.aliagent.orchestration.governance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InMemoryVersionRepository implements VersionRepository {
    private final List<ManagedVersion> versions = new ArrayList<>();
    private final List<TenantVersionAssignment> assignments = new ArrayList<>();
    private final Map<String, ExecutionVersionSet> pinned = new HashMap<>();

    InMemoryVersionRepository(List<ManagedVersion> versions) { this.versions.addAll(versions); }
    @Override public List<ManagedVersion> published(VersionType type) { return versions.stream().filter(v -> v.type() == type && "PUBLISHED".equals(v.status())).toList(); }
    @Override public List<TenantVersionAssignment> assignments(String tenantId, VersionType type) { return assignments.stream().filter(a -> a.tenantId().equals(tenantId) && a.versionType() == type && "ACTIVE".equals(a.status())).toList(); }
    @Override public void save(ManagedVersion version) { versions.removeIf(v -> v.id().equals(version.id())); versions.add(version); }
    @Override public void assign(TenantVersionAssignment assignment) { assignments.removeIf(a -> a.tenantId().equals(assignment.tenantId()) && a.versionType() == assignment.versionType()); assignments.add(assignment); }
    @Override public void rollback(VersionType type, java.util.UUID versionId) { versions.replaceAll(v -> v.type() == type && v.id().equals(versionId) ? new ManagedVersion(v.id(), v.type(), v.versionName(), "RETIRED") : v); }
    @Override public Optional<ExecutionVersionSet> pinned(String executionId) { return Optional.ofNullable(pinned.get(executionId)); }
    @Override public void pin(String executionId, ExecutionVersionSet versionSet) { pinned.putIfAbsent(executionId, versionSet); }
}
