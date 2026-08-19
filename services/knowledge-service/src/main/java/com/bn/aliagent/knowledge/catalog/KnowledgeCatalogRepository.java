package com.bn.aliagent.knowledge.catalog;

import java.util.UUID;
import com.bn.aliagent.knowledge.insight.KnowledgeInsightOutbox;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("database")
public class KnowledgeCatalogRepository {
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeGateDecisionPort gates;
    private final KnowledgeInsightOutbox insightOutbox;

    public KnowledgeCatalogRepository(JdbcTemplate jdbcTemplate, KnowledgeGateDecisionPort gates, KnowledgeInsightOutbox insightOutbox) {
        this.jdbcTemplate = jdbcTemplate; this.gates = gates; this.insightOutbox = insightOutbox;
    }

    public KnowledgeVersionState versionState(UUID versionId, String tenantId) {
        String state = jdbcTemplate.queryForObject("SELECT state FROM knowledge_version WHERE id = ? AND tenant_id = ?", String.class, versionId, tenantId);
        return KnowledgeVersionState.valueOf(state);
    }

    @Transactional
    public void publish(UUID versionId, String tenantId, String manifestDigest, String policyVersion, String proof) {
        gates.requirePass(tenantId, versionId, manifestDigest, policyVersion, proof);
        VersionTransitionPolicy.requirePublishable(versionState(versionId, tenantId));
        jdbcTemplate.update("UPDATE knowledge_version SET state = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ?", versionId, tenantId);
        insightOutbox.append(tenantId, versionId.toString(), versionId, "published-v1");
    }
}
