package com.bn.aliagent.orchestration.governance;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VersionGovernanceService {
    private final VersionRepository repository;

    public VersionGovernanceService() { this(new InMemoryVersionRepository(List.of())); }
    VersionGovernanceService(VersionRepository repository) { this.repository = repository; }

    public synchronized ExecutionVersionSet pin(String tenantId, String executionId) {
        return repository.pinned(executionId).orElseGet(() -> {
            ExecutionVersionSet versions = resolve(tenantId);
            repository.pin(executionId, versions);
            return versions;
        });
    }

    public ExecutionVersionSet resolve(String tenantId) {
        return new ExecutionVersionSet(resolve(tenantId, VersionType.PROMPT), resolve(tenantId, VersionType.WORKFLOW),
                resolve(tenantId, VersionType.MODEL), resolve(tenantId, VersionType.RULE));
    }

    public void publish(ManagedVersion version) { repository.save(new ManagedVersion(version.id(), version.type(), version.versionName(), "PUBLISHED")); }
    public void assign(TenantVersionAssignment assignment) { repository.assign(assignment); }
    public void rollback(VersionType type, UUID versionId) { repository.rollback(type, versionId); }

    private UUID resolve(String tenantId, VersionType type) {
        List<TenantVersionAssignment> assignments = repository.assignments(tenantId, type);
        int bucket = Math.floorMod((tenantId + ':' + type).hashCode(), 100);
        return assignments.stream().filter(a -> bucket < a.rolloutPercentage()).map(TenantVersionAssignment::versionId).findFirst()
                .orElseGet(() -> repository.published(type).stream().min(Comparator.comparing(ManagedVersion::id)).map(ManagedVersion::id)
                        .orElseThrow(() -> new IllegalStateException("不存在已发布的 " + type + " 版本")));
    }
}
