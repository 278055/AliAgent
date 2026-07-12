package com.bn.aliagent;

import com.bn.aliagent.Controller.HealthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 离线冒烟测试：只验证不依赖外部资源的健康探针契约。
 */
class AliAgentApplicationTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 独立装配控制器，避免启动数据库、Redis 与 DashScope 自动配置。
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
    }

    @Test
    void healthEndpointReturnsOkWithoutExternalDependencies() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("ok"));
    }

    @Test
    void healthStreamReturnsSingleOkEventWithoutExternalDependencies() throws Exception {
        mockMvc.perform(get("/api/health/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string("data:ok\n\n"));
    }
}
