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

@SpringBootTest
@AutoConfigureMockMvc
class VersionAdministrationControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void internalPublishRequiresServiceJwtAndAcceptsScopedJwt() throws Exception {
        mockMvc.perform(post("/internal/api/v1/orchestration/versions/publish").contentType("application/json")
                .content("{\"type\":\"MODEL\",\"versionName\":\"v1\"}"))
                .andExpect(status().isUnauthorized());
        String token = new ServiceJwtSupport("test-service-jwt-secret-must-be-at-least-32-bytes")
                .issue("gateway-service", "ai-orchestration-service", List.of("POST:/internal/api/v1/orchestration/versions/publish"));
        mockMvc.perform(post("/internal/api/v1/orchestration/versions/publish").header("X-Service-Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"type\":\"MODEL\",\"versionName\":\"v1\"}"))
                .andExpect(status().isAccepted());
    }
}
