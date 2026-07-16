CREATE TABLE ingestion_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id UUID NOT NULL REFERENCES ingestion_task(id) ON DELETE CASCADE,
    trace_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ingestion_outbox_pending ON ingestion_outbox (created_at) WHERE published_at IS NULL;
