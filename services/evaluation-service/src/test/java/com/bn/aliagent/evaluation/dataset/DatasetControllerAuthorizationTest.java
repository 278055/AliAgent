package com.bn.aliagent.evaluation.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DatasetControllerAuthorizationTest {
    @Test
    void rejectsClientForgeAndDerivesPublicAuthorizationFromTrustedPermission() {
        var forged = new DatasetController.TrustedDatasetContext("tenant", "attacker");
        assertThrows(SecurityException.class, forged::publicationAuthorization);
        var authorized = new DatasetController.TrustedDatasetContext("tenant", "admin", Set.of("EVALUATION_DATASET_PUBLIC_PUBLISH"));
        assertEquals("admin", authorized.publicationAuthorization().authorizedBy());
    }
}
