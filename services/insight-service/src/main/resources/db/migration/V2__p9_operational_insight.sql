CREATE TABLE insight_event_inbox (
    tenant_id VARCHAR(128) NOT NULL, producer VARCHAR(128) NOT NULL, event_id UUID NOT NULL,
    content_digest VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (tenant_id, producer, event_id)
);
CREATE TABLE insight_fact (
    fact_id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, type VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL, dimensions JSONB NOT NULL, measures JSONB NOT NULL,
    evidence_refs JSONB NOT NULL, source_event_id UUID NOT NULL, revision INT NOT NULL,
    supersedes_fact_id UUID, anonymization_rule_version VARCHAR(64) NOT NULL,
    UNIQUE (tenant_id, source_event_id, revision)
);
CREATE TABLE insight_fact_revision (tenant_id VARCHAR(128) NOT NULL, fact_id UUID NOT NULL, revision INT NOT NULL, supersedes_fact_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (tenant_id, fact_id, revision));
CREATE TABLE insight_aggregate (tenant_id VARCHAR(128) NOT NULL, metric VARCHAR(128) NOT NULL, definition_version INT NOT NULL, granularity VARCHAR(16) NOT NULL, window_start TIMESTAMPTZ NOT NULL, dimensions JSONB NOT NULL, revision INT NOT NULL, numerator BIGINT NOT NULL, denominator BIGINT NOT NULL, created_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (tenant_id, metric, definition_version, granularity, window_start, dimensions, revision));
CREATE TABLE insight_metric_definition (tenant_id VARCHAR(128) NOT NULL, metric VARCHAR(128) NOT NULL, version INT NOT NULL, definition JSONB NOT NULL, active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (tenant_id, metric, version));
CREATE TABLE insight_threshold (tenant_id VARCHAR(128) NOT NULL, metric VARCHAR(128) NOT NULL, version INT NOT NULL, rule JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (tenant_id, metric, version));
CREATE TABLE insight_radar (radar_id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, problem_type VARCHAR(128) NOT NULL, dimensions JSONB NOT NULL, window_start TIMESTAMPTZ NOT NULL, window_end TIMESTAMPTZ NOT NULL, metric_version INT NOT NULL, threshold_version INT NOT NULL, aggregate_revision INT NOT NULL, status VARCHAR(32) NOT NULL, version BIGINT NOT NULL);
CREATE TABLE insight_radar_audit (audit_id UUID PRIMARY KEY, radar_id UUID NOT NULL REFERENCES insight_radar(radar_id), tenant_id VARCHAR(128) NOT NULL, operator_id VARCHAR(128) NOT NULL, action VARCHAR(64) NOT NULL, reason VARCHAR(512) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE insight_topic (topic_id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, risk VARCHAR(32) NOT NULL, state VARCHAR(64) NOT NULL, display_name VARCHAR(256), summary TEXT, model_version VARCHAR(128), created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE insight_topic_member (topic_id UUID NOT NULL REFERENCES insight_topic(topic_id), tenant_id VARCHAR(128) NOT NULL, member_ref VARCHAR(256) NOT NULL, PRIMARY KEY (topic_id, member_ref));
CREATE TABLE insight_topic_review (review_id UUID PRIMARY KEY, topic_id UUID NOT NULL REFERENCES insight_topic(topic_id), tenant_id VARCHAR(128) NOT NULL, supervisor_id VARCHAR(128) NOT NULL, decision VARCHAR(32) NOT NULL, reason VARCHAR(512) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE insight_topic_vector (tenant_id VARCHAR(128) NOT NULL, problem_type VARCHAR(128) NOT NULL, member_ref VARCHAR(256) NOT NULL, cluster_id VARCHAR(256) NOT NULL, embedding public.vector(1024) NOT NULL, PRIMARY KEY (tenant_id, member_ref));
CREATE TABLE insight_knowledge_gap (gap_id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, problem_type VARCHAR(128) NOT NULL, signals JSONB NOT NULL, knowledge_version VARCHAR(128), evidence_count BIGINT NOT NULL, status VARCHAR(32) NOT NULL, knowledge_published BOOLEAN NOT NULL DEFAULT false);
CREATE TABLE insight_recalculation_queue (request_id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, fact_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, decision VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE insight_security_audit (audit_id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, actor_id VARCHAR(128) NOT NULL, action VARCHAR(128) NOT NULL, resource_ref VARCHAR(256), result VARCHAR(32) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX insight_fact_tenant_time_idx ON insight_fact(tenant_id, occurred_at);
CREATE INDEX insight_radar_tenant_status_idx ON insight_radar(tenant_id, status);
