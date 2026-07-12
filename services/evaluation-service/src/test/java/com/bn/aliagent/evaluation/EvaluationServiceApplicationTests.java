package com.bn.aliagent.evaluation;

import com.bn.platform.security.ServiceJwtSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@AutoConfigureMockMvc
class EvaluationServiceApplicationTests {
    @Autowired private MockMvc mockMvc;
    @Test void 应返回最小健康契约() throws Exception { mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.status").value("UP")); }
    @Test void 缺失服务令牌应被拒绝() throws Exception { mockMvc.perform(post("/api/v1/events").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-401-001")); }
    @Test void 有效服务令牌应被接受() throws Exception {
        String token = new ServiceJwtSupport("test-service-jwt-secret-must-be-at-least-32-bytes").issue("gateway-service", "evaluation-service", List.of("POST:/api/v1/events"));
        mockMvc.perform(post("/api/v1/events").header("X-Service-Authorization", "Bearer " + token).contentType("application/json").content("{}"))
                .andExpect(status().isAccepted());
    }
}
