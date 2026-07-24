package com.bn.aliagent.orchestration.adapter;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ChatModelPort;

public final class MockChatAdapter implements ChatModelPort {
    @Override public String generate(OrchestrationContract.ExecutionContext context, String prompt) {
        if (prompt == null || prompt.isBlank()) throw new AdapterException(AdapterException.Category.VALIDATION, "prompt must not be blank");
        return "Mock response: " + prompt;
    }
}
