CREATE TABLE conversation_verified_tag (
    tenant_id VARCHAR(128) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    tag_code VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL CHECK (source IN ('RULE_ENGINE','AFTERSALE_VERIFIED','ORDER_VERIFIED')),
    verified_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, conversation_id, tag_code)
);
CREATE INDEX idx_conversation_verified_tag_route ON conversation_verified_tag (tenant_id, conversation_id, tag_code);
