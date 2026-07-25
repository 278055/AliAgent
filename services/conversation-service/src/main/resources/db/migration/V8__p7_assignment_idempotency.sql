CREATE TABLE human_assignment_result (
    tenant_id VARCHAR(128) NOT NULL,
    request_id UUID NOT NULL,
    accepted BOOLEAN NOT NULL,
    takeover_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, request_id)
);
