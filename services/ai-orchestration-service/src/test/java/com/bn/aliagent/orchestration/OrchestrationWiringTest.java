package com.bn.aliagent.orchestration;

import com.bn.aliagent.orchestration.core.ExecutionStore;
import com.bn.aliagent.orchestration.core.OrchestrationService;
import com.bn.aliagent.orchestration.messaging.AiReplyRequestedV2Consumer;
import com.bn.aliagent.orchestration.messaging.AiReplyRequestedV2Mapper;
import com.bn.aliagent.orchestration.runtime.ReadOnlyWorkflowRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class OrchestrationWiringTest {
    @Autowired private AiReplyRequestedV2Consumer consumer;
    @Autowired private AiReplyRequestedV2Mapper mapper;
    @Autowired private ExecutionStore store;
    @Autowired private OrchestrationService service;
    @Autowired private ReadOnlyWorkflowRunner runner;

    @Test
    void 注册v2消费端和执行管道() {
        assertNotNull(consumer);
        assertNotNull(mapper);
        assertNotNull(store);
        assertNotNull(service);
        assertNotNull(runner);
    }
}
