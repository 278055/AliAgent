package com.bn.aliagent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeVideoAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration")
@AutoConfigureMockMvc
class KnowledgeServiceApplicationTests {
    @Autowired private MockMvc mockMvc;
    @Test void 应返回最小健康契约() throws Exception { mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.status").value("UP")); }
}
