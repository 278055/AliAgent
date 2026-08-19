package com.bn.aliagent.insight.persistence;

import com.bn.aliagent.insight.fact.AnonymizedFact;
import com.bn.aliagent.insight.fact.InsightFactSink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcInsightFactRepository implements InsightFactSink {
    private final JdbcTemplate jdbc; private final ObjectMapper json = new ObjectMapper();
    public JdbcInsightFactRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void append(AnonymizedFact fact) {
        try { jdbc.update("INSERT INTO insight_fact (fact_id, tenant_id, type, occurred_at, dimensions, measures, evidence_refs, source_event_id, revision, supersedes_fact_id, anonymization_rule_version) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)", fact.factId(), fact.tenantId(), fact.type(), Timestamp.from(fact.occurredAt()), json.writeValueAsString(fact.dimensions()), json.writeValueAsString(fact.measures()), json.writeValueAsString(fact.evidenceRefs()), fact.sourceEventId(), fact.revision(), fact.supersedesFactId(), fact.anonymizationRuleVersion()); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("事实序列化失败", exception); }
    }
}
