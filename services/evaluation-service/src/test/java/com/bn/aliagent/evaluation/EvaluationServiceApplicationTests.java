package com.bn.aliagent.evaluation;

import com.bn.platform.security.ServiceJwtSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "SERVICE_JWT_SECRET=test-service-jwt-secret-must-be-at-least-32-bytes", "evaluation.anonymization.key=test-evaluation-anonymization-key-must-be-at-least-32-bytes"})
@AutoConfigureMockMvc
class EvaluationServiceApplicationTests {
    @Autowired private MockMvc mockMvc;
    @Test void 应返回最小健康契约() throws Exception { mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.status").value("UP")); }
    @Test void 缺失服务令牌应被拒绝() throws Exception { mockMvc.perform(post("/api/v1/events").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-401-001")); }
    @Test void 有效服务令牌应被接受() throws Exception {
        String token = new ServiceJwtSupport("test-service-jwt-secret-must-be-at-least-32-bytes").issue("gateway-service", "evaluation-service", List.of("POST:/api/v1/events"));
        String tenantId = "test-p8-integration";
        String event = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"conversation.feedback.received\",\"eventVersion\":1,\"occurredAt\":\""
                + Instant.now() + "\",\"tenantId\":\"" + tenantId + "\",\"traceId\":\"" + UUID.randomUUID()
                + "\",\"producer\":\"test\",\"payload\":{\"message\":\"test feedback\"}}";
        mockMvc.perform(post("/api/v1/events").header("X-Service-Authorization", "Bearer " + token).header("X-Tenant-Id", tenantId)
                        .contentType("application/json").content(event))
                .andExpect(status().isAccepted());
    }
}
