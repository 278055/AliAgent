package com.bn.aliagent.orchestration.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VersionGovernanceServiceTest {
    @Test
    void resolvesPublishedDefaultsAndKeepsExecutionPinStable() {
        UUID prompt = UUID.randomUUID();
        UUID workflow = UUID.randomUUID();
        UUID model = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        VersionGovernanceService service = new VersionGovernanceService(new InMemoryVersionRepository(List.of(
                version(prompt, VersionType.PROMPT, "PUBLISHED"), version(workflow, VersionType.WORKFLOW, "PUBLISHED"),
                version(model, VersionType.MODEL, "PUBLISHED"), version(rule, VersionType.RULE, "PUBLISHED"))));

        ExecutionVersionSet first = service.pin("test-p5-c-tenant", "execution-1");
        service.publish(version(UUID.randomUUID(), VersionType.MODEL, "PUBLISHED"));

        assertEquals(model, first.modelVersionId());
        assertEquals(first, service.pin("test-p5-c-tenant", "execution-1"));
    }

    @Test
    void tenantAssignmentUsesStableRolloutBucket() {
        UUID defaultModel = UUID.randomUUID();
        UUID canaryModel = UUID.randomUUID();
        InMemoryVersionRepository repository = new InMemoryVersionRepository(List.of(
                version(UUID.randomUUID(), VersionType.PROMPT, "PUBLISHED"), version(UUID.randomUUID(), VersionType.WORKFLOW, "PUBLISHED"),
                version(defaultModel, VersionType.MODEL, "PUBLISHED"), version(UUID.randomUUID(), VersionType.RULE, "PUBLISHED"),
                version(canaryModel, VersionType.MODEL, "PUBLISHED")));
        repository.assign(new TenantVersionAssignment("tenant-a", VersionType.MODEL, canaryModel, 100, "ACTIVE"));

        assertEquals(canaryModel, new VersionGovernanceService(repository).resolve("tenant-a").modelVersionId());
    }

    private static ManagedVersion version(UUID id, VersionType type, String status) {
        return new ManagedVersion(id, type, "v1", status);
    }
}
