package com.bn.aliagent.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayServiceApplicationTests {
    @LocalServerPort private int port;

    @Test
    void 应返回最小健康契约() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build().get().uri("/api/v1/health")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.status").isEqualTo("UP");
    }
}
