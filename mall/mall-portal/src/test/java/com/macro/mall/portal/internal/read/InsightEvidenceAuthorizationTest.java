package com.macro.mall.portal.internal.read;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class InsightEvidenceAuthorizationTest {
    @Test
    void onlySupervisorCanRequestEvidenceVerification() {
        InsightEvidenceAuthorization authorization = new InsightEvidenceAuthorization();

        assertThrows(InternalAccessDeniedException.class, () -> authorization.requireSupervisor(
                new UserSnapshot("test-tenant", 1L, SubjectType.STAFF, Set.of("INSIGHT_OPERATOR"))));
    }
}
