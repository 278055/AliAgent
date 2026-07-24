CREATE TABLE copilot_suggestion (
    id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, conversation_id UUID NOT NULL, trigger_message_id UUID NOT NULL, assigned_agent_id VARCHAR(128) NOT NULL,
    refresh_no INTEGER NOT NULL CHECK (refresh_no >= 0), visibility VARCHAR(16) NOT NULL CHECK (visibility = 'PRIVATE'),
    status VARCHAR(32) NOT NULL CHECK (status IN ('GENERATED','FAILED_RETRYABLE','ACCEPTED','MODIFIED','IGNORED')),
    original_content TEXT NOT NULL, final_content TEXT NOT NULL DEFAULT '', diff_summary TEXT NOT NULL DEFAULT '', citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_version VARCHAR(128) NOT NULL, prompt_version VARCHAR(128) NOT NULL, workflow_version VARCHAR(128) NOT NULL,
    request_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (tenant_id, request_id),
    UNIQUE (tenant_id, conversation_id, trigger_message_id, workflow_version, refresh_no)
);
CREATE TABLE copilot_suggestion_action (id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, suggestion_id UUID NOT NULL REFERENCES copilot_suggestion(id), request_id UUID NOT NULL, action_type VARCHAR(32) NOT NULL CHECK (action_type IN ('ACCEPTED','MODIFIED','IGNORED')), original_content TEXT NOT NULL, final_content TEXT NOT NULL, diff_summary TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (tenant_id, request_id));
CREATE TABLE copilot_inbox (event_id UUID NOT NULL, consumer_name VARCHAR(100) NOT NULL, tenant_id VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMPTZ, PRIMARY KEY (event_id, consumer_name));
CREATE TABLE copilot_outbox (id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, request_id UUID NOT NULL, topic VARCHAR(128) NOT NULL, event_type VARCHAR(128) NOT NULL, event_version INTEGER NOT NULL, payload JSONB NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (tenant_id, request_id, event_type));
