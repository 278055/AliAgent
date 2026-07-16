CREATE TABLE knowledge_document (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    content_length BIGINT NOT NULL CHECK (content_length >= 0),
    checksum_sha256 CHAR(64) NOT NULL,
    uploaded_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, object_key)
);

CREATE TABLE knowledge_version (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    document_id UUID NOT NULL REFERENCES knowledge_document(id),
    version_number INTEGER NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (state IN ('DRAFT', 'PROCESSING', 'READY_FOR_REVIEW', 'PUBLISHED', 'RETIRED', 'FAILED')),
    created_by VARCHAR(128) NOT NULL,
    failure_diagnostic TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, document_id, version_number)
);

CREATE TABLE knowledge_chunk (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    version_id UUID NOT NULL REFERENCES knowledge_version(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, version_id, sequence_number)
);

CREATE INDEX idx_knowledge_version_visible ON knowledge_version (tenant_id, state) WHERE state = 'PUBLISHED';
CREATE INDEX idx_knowledge_chunk_tsv ON knowledge_chunk USING GIN (content_tsv);
CREATE INDEX idx_knowledge_chunk_embedding ON knowledge_chunk USING HNSW (embedding vector_cosine_ops);

CREATE TABLE ingestion_task (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    version_id UUID NOT NULL REFERENCES knowledge_version(id),
    state VARCHAR(32) NOT NULL CHECK (state IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    event_id UUID NOT NULL UNIQUE,
    trace_id VARCHAR(128) NOT NULL,
    failure_diagnostic TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ingestion_task_pending ON ingestion_task (tenant_id, state, created_at);

CREATE TABLE consumed_event (
    event_id UUID NOT NULL,
    consumer VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer)
);
