CREATE TABLE conversation_business_context (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    linked_order_id BIGINT,
    linked_after_sale_id VARCHAR(128),
    bound_by_subject_id VARCHAR(128) NOT NULL,
    binding_source VARCHAR(32) NOT NULL,
    request_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_conversation_business_context_link CHECK (linked_order_id IS NOT NULL OR linked_after_sale_id IS NOT NULL),
    CONSTRAINT ux_conversation_business_context_conversation UNIQUE (tenant_id, conversation_id)
);

CREATE UNIQUE INDEX ux_conversation_business_context_request
    ON conversation_business_context (tenant_id, bound_by_subject_id, request_id);
