package com.bn.aliagent.conversation.agent;

import java.util.List;
import java.util.UUID;

public interface AgentDirectoryPort {
    List<AgentModels.AgentSnapshot> candidates(String tenantId, UUID skillGroupId);
}
