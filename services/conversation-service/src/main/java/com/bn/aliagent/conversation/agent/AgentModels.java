package com.bn.aliagent.conversation.agent;

import java.time.Instant;
import java.util.UUID;

public final class AgentModels {
    private AgentModels() { }

    public enum Presence { OFFLINE, ONLINE, BUSY }

    public record Membership(String tenantId, UUID skillGroupId, String staffId, boolean enabled, int maxConcurrent) {
        public Membership {
            require(tenantId, "tenantId");
            if (skillGroupId == null) throw new IllegalArgumentException("skillGroupId is required");
            require(staffId, "staffId");
            if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent must be positive");
        }
    }

    public record AgentSnapshot(String tenantId, UUID skillGroupId, String staffId, Presence presence,
                                boolean membershipEnabled, int maxConcurrent, int activeCount, Instant lastAssignedAt) {
        public AgentSnapshot {
            require(tenantId, "tenantId");
            if (skillGroupId == null) throw new IllegalArgumentException("skillGroupId is required");
            require(staffId, "staffId");
            if (presence == null) throw new IllegalArgumentException("presence is required");
            if (maxConcurrent < 1 || activeCount < 0) throw new IllegalArgumentException("capacity is invalid");
        }

        public boolean online() { return presence == Presence.ONLINE; }
        public boolean hasCapacity() { return activeCount < maxConcurrent; }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
