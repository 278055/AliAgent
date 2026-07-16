CREATE TABLE knowledge_domain (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code)
);

CREATE TABLE knowledge_document_domain (
    tenant_id VARCHAR(128) NOT NULL,
    document_id UUID NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    domain_id UUID NOT NULL REFERENCES knowledge_domain(id) ON DELETE RESTRICT,
    PRIMARY KEY (tenant_id, document_id, domain_id)
);

CREATE INDEX idx_knowledge_document_domain_lookup ON knowledge_document_domain (tenant_id, domain_id, document_id);

CREATE TABLE knowledge_authorization_snapshot (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    subject_type VARCHAR(32) NOT NULL CHECK (subject_type IN ('MEMBER', 'STAFF')),
    roles_csv TEXT NOT NULL,
    permissions_csv TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (expires_at > issued_at)
);

CREATE INDEX idx_knowledge_authorization_snapshot_subject ON knowledge_authorization_snapshot (tenant_id, subject_id, expires_at);

CREATE TABLE knowledge_authorization_snapshot_domain (
    snapshot_id UUID NOT NULL REFERENCES knowledge_authorization_snapshot(id) ON DELETE CASCADE,
    tenant_id VARCHAR(128) NOT NULL,
    domain_id UUID NOT NULL REFERENCES knowledge_domain(id) ON DELETE CASCADE,
    permission VARCHAR(128) NOT NULL,
    PRIMARY KEY (snapshot_id, domain_id, permission)
);

CREATE INDEX idx_knowledge_authorization_snapshot_domain_filter ON knowledge_authorization_snapshot_domain (tenant_id, snapshot_id, domain_id, permission);
