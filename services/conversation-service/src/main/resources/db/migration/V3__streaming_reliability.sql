CREATE TABLE conversation_generation (
    generation_id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    message_id UUID NOT NULL REFERENCES message(id),
    request_id UUID NOT NULL,
    message_sequence BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_chunk_index INTEGER NOT NULL DEFAULT -1,
    checkpoint_content TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_conversation_generation_message ON conversation_generation (tenant_id, conversation_id, message_id);
CREATE INDEX idx_conversation_generation_replay ON conversation_generation (tenant_id, conversation_id, message_sequence) WHERE status <> 'STREAMING';
