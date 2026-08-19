CREATE TABLE knowledge_insight_outbox (
    event_id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, version_id UUID NOT NULL REFERENCES knowledge_version(id),
    trace_id VARCHAR(128) NOT NULL, event_type VARCHAR(64) NOT NULL, coverage_version VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_knowledge_insight_outbox_pending ON knowledge_insight_outbox(created_at) WHERE published_at IS NULL;
