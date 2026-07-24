package com.bn.aliagent.orchestration.adapter;

import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ChatModelPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdapterConfiguration {
    @Bean
    ChatModelPort chatModelPort(@Value("${orchestration.model.provider:mock}") String provider,
            @Value("${orchestration.model.dashscope-api-key:}") String apiKey) {
        if ("dashscope".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank()) {
            return new DashScopeChatAdapter(apiKey, "https://dashscope.aliyuncs.com");
        }
        return new MockChatAdapter();
    }
}
