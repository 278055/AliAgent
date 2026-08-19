CREATE TABLE orchestration_insight_outbox (
    event_id UUID PRIMARY KEY, tenant_id VARCHAR(100) NOT NULL, execution_id UUID NOT NULL REFERENCES orchestration_execution(id),
    trace_id VARCHAR(100) NOT NULL, event_type VARCHAR(64) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_orchestration_insight_outbox_pending ON orchestration_insight_outbox(created_at) WHERE published_at IS NULL;
