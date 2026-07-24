package com.bn.aliagent.conversation.assignment;

import com.bn.aliagent.conversation.agent.AgentModels;
import java.util.List;
import java.util.UUID;

public interface AssignmentPorts {
    List<AgentModels.AgentSnapshot> candidates(String tenantId, UUID skillGroupId);
}
