package com.bn.aliagent.orchestration.governance;

import com.bn.platform.security.ServiceJwtSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureMockMvc
class VersionAdministrationControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void internalPublishRequiresServiceJwtAndGateDecision() throws Exception {
        mockMvc.perform(post("/internal/api/v1/orchestration/versions/publish").contentType("application/json")
                .content("{\"tenantId\":\"test-p8\",\"type\":\"MODEL\",\"versionName\":\"v1\",\"manifestDigest\":\"m\",\"policyVersion\":\"p\",\"gateProof\":\"proof\"}"))
                .andExpect(status().isUnauthorized());
        String token = new ServiceJwtSupport("test-service-jwt-secret-must-be-at-least-32-bytes")
                .issue("gateway-service", "ai-orchestration-service", List.of("POST:/internal/api/v1/orchestration/versions/publish"));
        assertThrows(Exception.class, () -> mockMvc.perform(post("/internal/api/v1/orchestration/versions/publish").header("X-Service-Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"tenantId\":\"test-p8\",\"type\":\"MODEL\",\"versionName\":\"v1\",\"manifestDigest\":\"m\",\"policyVersion\":\"p\",\"gateProof\":\"proof\"}"))
                .andReturn());
    }
}
