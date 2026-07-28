package com.bn.aliagent.orchestration.governance;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VersionGovernanceService {
    private final VersionRepository repository;
    private final GateDecisionPort gates;

    @Autowired
    public VersionGovernanceService(GateDecisionPort gates) { this(new InMemoryVersionRepository(List.of()), gates); }
    public VersionGovernanceService() { this(new InMemoryVersionRepository(List.of()), rejectAll()); }
    VersionGovernanceService(VersionRepository repository) { this(repository, rejectAll()); }
    VersionGovernanceService(VersionRepository repository, GateDecisionPort gates) { this.repository = repository; this.gates = gates; }

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

    public void publish(String tenantId, ManagedVersion version, String manifestDigest, String policyVersion, String proof) {
        gates.requirePass(tenantId, version.type(), version.id(), manifestDigest, policyVersion, proof);
        repository.save(new ManagedVersion(version.id(), version.type(), version.versionName(), "PUBLISHED"));
    }
    public void assign(TenantVersionAssignment assignment, String manifestDigest, String policyVersion, String proof) {
        gates.requirePass(assignment.tenantId(), assignment.versionType(), assignment.versionId(), manifestDigest, policyVersion, proof);
        repository.assign(assignment);
    }
    public void rollback(VersionType type, UUID versionId) { repository.rollback(type, versionId); }

    private UUID resolve(String tenantId, VersionType type) {
        List<TenantVersionAssignment> assignments = repository.assignments(tenantId, type);
        int bucket = Math.floorMod((tenantId + ':' + type).hashCode(), 100);
        return assignments.stream().filter(a -> bucket < a.rolloutPercentage()).map(TenantVersionAssignment::versionId).findFirst()
                .orElseGet(() -> repository.published(type).stream().min(Comparator.comparing(ManagedVersion::id)).map(ManagedVersion::id)
                        .orElseThrow(() -> new IllegalStateException("不存在已发布的 " + type + " 版本")));
    }
    private static GateDecisionPort rejectAll() { return (tenantId, type, versionId, manifestDigest, policyVersion, proof) -> { throw new SecurityException("缺少可验证的 PASS Gate Decision"); }; }
}
