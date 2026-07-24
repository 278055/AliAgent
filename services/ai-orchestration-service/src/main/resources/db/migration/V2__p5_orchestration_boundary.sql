CREATE TABLE orchestration_inbox (
    event_id UUID NOT NULL, consumer_name VARCHAR(100) NOT NULL, tenant_id VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ, PRIMARY KEY (event_id, consumer_name)
);
CREATE TABLE orchestration_execution (
    id UUID PRIMARY KEY, event_id UUID NOT NULL UNIQUE, tenant_id VARCHAR(100) NOT NULL, conversation_id UUID NOT NULL,
    message_id UUID NOT NULL, generation_id UUID NOT NULL, request_id UUID NOT NULL, trace_id VARCHAR(100) NOT NULL,
    subject_id VARCHAR(100) NOT NULL, subject_type VARCHAR(16) NOT NULL, authorization_snapshot_id UUID NOT NULL,
    intent_type VARCHAR(32), workflow_type VARCHAR(32), status VARCHAR(32) NOT NULL, degradation_reason VARCHAR(32),
    prompt_version_id UUID, workflow_version_id UUID, model_version_id UUID, rule_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE orchestration_state_transition (
    id BIGSERIAL PRIMARY KEY, execution_id UUID NOT NULL REFERENCES orchestration_execution(id), from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL, reason VARCHAR(100), occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE prompt_version (id UUID PRIMARY KEY, tenant_id VARCHAR(100), version_name VARCHAR(100) NOT NULL, content TEXT NOT NULL, status VARCHAR(16) NOT NULL, published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE workflow_version (id UUID PRIMARY KEY, tenant_id VARCHAR(100), version_name VARCHAR(100) NOT NULL, definition JSONB NOT NULL, status VARCHAR(16) NOT NULL, published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE model_version (id UUID PRIMARY KEY, tenant_id VARCHAR(100), version_name VARCHAR(100) NOT NULL, provider VARCHAR(32) NOT NULL, model_name VARCHAR(100) NOT NULL, parameters JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(16) NOT NULL, published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE rule_version (id UUID PRIMARY KEY, tenant_id VARCHAR(100), version_name VARCHAR(100) NOT NULL, definition JSONB NOT NULL, status VARCHAR(16) NOT NULL, published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE tenant_version_assignment (id UUID PRIMARY KEY, tenant_id VARCHAR(100) NOT NULL, version_type VARCHAR(32) NOT NULL, version_id UUID NOT NULL, rollout_percentage INTEGER NOT NULL CHECK (rollout_percentage BETWEEN 0 AND 100), status VARCHAR(16) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE tool_invocation_audit (id UUID PRIMARY KEY, execution_id UUID NOT NULL REFERENCES orchestration_execution(id), tenant_id VARCHAR(100) NOT NULL, tool_type VARCHAR(16) NOT NULL, tool_name VARCHAR(100) NOT NULL, outcome VARCHAR(32) NOT NULL, redacted_request JSONB NOT NULL DEFAULT '{}'::jsonb, redacted_response JSONB NOT NULL DEFAULT '{}'::jsonb, occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE model_invocation_audit (id UUID PRIMARY KEY, execution_id UUID NOT NULL REFERENCES orchestration_execution(id), tenant_id VARCHAR(100) NOT NULL, model_version_id UUID, outcome VARCHAR(32) NOT NULL, latency_ms BIGINT, token_usage JSONB NOT NULL DEFAULT '{}'::jsonb, occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_orchestration_execution_recovery ON orchestration_execution (status, updated_at);
CREATE INDEX idx_tenant_version_assignment_lookup ON tenant_version_assignment (tenant_id, version_type, status);
