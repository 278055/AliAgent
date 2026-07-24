package com.bn.aliagent.orchestration.governance;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/orchestration/versions")
class VersionAdministrationController {
    private final VersionGovernanceService versions;
    VersionAdministrationController(VersionGovernanceService versions) { this.versions = versions; }

    @PostMapping("/publish") @ResponseStatus(HttpStatus.ACCEPTED)
    void publish(@RequestBody PublishRequest request) { versions.publish(new ManagedVersion(UUID.randomUUID(), request.type(), request.versionName(), "PUBLISHED")); }
    @PostMapping("/assign") @ResponseStatus(HttpStatus.ACCEPTED)
    void assign(@RequestBody AssignmentRequest request) { versions.assign(new TenantVersionAssignment(request.tenantId(), request.type(), request.versionId(), request.rolloutPercentage(), "ACTIVE")); }
    @PostMapping("/rollback") @ResponseStatus(HttpStatus.ACCEPTED)
    void rollback(@RequestBody RollbackRequest request) { versions.rollback(request.type(), request.versionId()); }

    record PublishRequest(VersionType type, String versionName) { }
    record AssignmentRequest(String tenantId, VersionType type, UUID versionId, int rolloutPercentage) { }
    record RollbackRequest(VersionType type, UUID versionId) { }
}
