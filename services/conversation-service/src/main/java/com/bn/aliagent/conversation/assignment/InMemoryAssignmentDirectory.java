package com.bn.aliagent.conversation.assignment;

import com.bn.aliagent.conversation.agent.AgentModels;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InMemoryAssignmentDirectory implements AssignmentPorts {
    private final List<AgentModels.AgentSnapshot> agents = new ArrayList<>();
    public synchronized void set(List<AgentModels.AgentSnapshot> value) { agents.clear(); agents.addAll(value); }
    @Override public synchronized List<AgentModels.AgentSnapshot> candidates(String tenantId, UUID groupId) { return agents.stream().filter(a -> a.tenantId().equals(tenantId) && a.skillGroupId().equals(groupId)).toList(); }
}
