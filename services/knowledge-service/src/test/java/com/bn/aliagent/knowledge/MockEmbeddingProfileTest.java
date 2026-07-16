package com.bn.aliagent.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class MockEmbeddingProfileTest {
    @Test
    void mockProfileCanStartWithoutDashScopeKeyAndProvides1024DimensionEmbedding() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(KnowledgeServiceApplication.class)
                .profiles("mock").properties("server.port=0").run()) {
            EmbeddingModel model = context.getBean(EmbeddingModel.class);
            assertEquals(1024, model.embed("测试").length);
        }
    }
}
