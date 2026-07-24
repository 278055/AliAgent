CREATE TABLE conversation_realtime_connection (
    connection_id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    instance_id VARCHAR(128) NOT NULL,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_realtime_connection_route ON conversation_realtime_connection (tenant_id, conversation_id, expires_at);

CREATE TABLE conversation_human_state (
    tenant_id VARCHAR(128) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    staff_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, conversation_id)
);

CREATE TABLE conversation_agent_presence (
    tenant_id VARCHAR(128) NOT NULL,
    staff_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('ONLINE', 'OFFLINE', 'BUSY')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, staff_id)
);
