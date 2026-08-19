package com.bn.aliagent.insight.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MallEvidenceVerificationClientTest {
    @Test
    void supervisorVerificationIsTenantScopedAndReturnsTransientEvidenceOnly() {
        var client = new MallEvidenceVerificationClient((request, supervisor) ->
                new MallEvidenceVerificationClient.VerificationResult(request.tenantId(), request.evidenceRef(), "PAID", "test-ref"));
        UUID radarId = UUID.randomUUID();

        var result = client.verify(new MallEvidenceVerificationClient.Request(radarId, "test-tenant", "test-evidence", "investigate anomaly", "service-jwt"),
                new MallEvidenceVerificationClient.Supervisor("supervisor-1", "test-tenant", Set.of("SUPERVISOR")));

        assertEquals("PAID", result.status());
        assertThrows(SecurityException.class, () -> client.verify(new MallEvidenceVerificationClient.Request(radarId, "test-tenant", "test-evidence", "reason", "service-jwt"),
                new MallEvidenceVerificationClient.Supervisor("operator-1", "test-tenant", Set.of("INSIGHT_OPERATOR"))));
    }
}
