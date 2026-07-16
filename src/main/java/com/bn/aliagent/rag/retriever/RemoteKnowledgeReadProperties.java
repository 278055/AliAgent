package com.bn.aliagent.rag.retriever;

import java.time.Duration;
import java.util.List;

/** feature.knowledge.remote-read 的单体侧配置。 */
public record RemoteKnowledgeReadProperties(boolean enabled, List<String> tenantWhitelist, boolean dualRun,
        String baseUrl, Duration timeout) {
    public RemoteKnowledgeReadProperties {
        tenantWhitelist = tenantWhitelist == null ? List.of() : List.copyOf(tenantWhitelist);
    }

    public boolean isTenantEnabled(String tenantId) {
        return enabled && tenantWhitelist.contains(tenantId);
    }
}
