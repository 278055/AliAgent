ALTER TABLE evaluation_dataset
    ADD CONSTRAINT uq_evaluation_dataset_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE evaluation_dataset_version
    ADD CONSTRAINT uq_evaluation_dataset_version_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE evaluation_gate_decision
    ADD CONSTRAINT uq_evaluation_gate_decision_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE evaluation_dataset_draft_sample (
    tenant_id VARCHAR(128) NOT NULL,
    draft_id UUID NOT NULL,
    sample_id UUID NOT NULL,
    dataset_id UUID NOT NULL,
    source_document_id UUID,
    sample_payload JSONB NOT NULL,
    sample_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, draft_id, sample_id),
    FOREIGN KEY (tenant_id, dataset_id) REFERENCES evaluation_dataset (tenant_id, id),
    UNIQUE (tenant_id, draft_id, sample_order)
);

CREATE TABLE evaluation_run_manifest (
    tenant_id VARCHAR(128) NOT NULL,
    run_id UUID NOT NULL,
    dataset_version_id UUID NOT NULL,
    draft_id UUID,
    manifest_payload JSONB NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(255),
    prompt_version VARCHAR(255),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, run_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id),
    FOREIGN KEY (tenant_id, dataset_version_id) REFERENCES evaluation_dataset_version (tenant_id, id)
);

CREATE TABLE evaluation_metric_evidence (
    tenant_id VARCHAR(128) NOT NULL,
    evidence_id UUID NOT NULL,
    run_id UUID NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    metric_value NUMERIC(18, 6),
    evidence_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, evidence_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id),
    UNIQUE (tenant_id, run_id, metric_name, evidence_id)
);

CREATE TABLE evaluation_dashscope_approval (
    tenant_id VARCHAR(128) NOT NULL,
    approval_id UUID NOT NULL,
    run_id UUID NOT NULL,
    approval_status VARCHAR(64) NOT NULL,
    approval_payload JSONB NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, approval_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id),
    UNIQUE (tenant_id, run_id, approval_id)
);

CREATE TABLE evaluation_budget_ledger (
    tenant_id VARCHAR(128) NOT NULL,
    ledger_id UUID NOT NULL,
    run_id UUID,
    budget_scope VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 6) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    entry_type VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, ledger_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id)
);

CREATE TABLE evaluation_gate_proof (
    tenant_id VARCHAR(128) NOT NULL,
    proof_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    canonical_payload JSONB NOT NULL,
    signature TEXT NOT NULL,
    key_id VARCHAR(255) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, proof_id),
    FOREIGN KEY (tenant_id, decision_id) REFERENCES evaluation_gate_decision (tenant_id, id),
    UNIQUE (tenant_id, decision_id)
);

CREATE TABLE evaluation_audit (
    tenant_id VARCHAR(128) NOT NULL,
    audit_id UUID NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    actor_id VARCHAR(255),
    event_payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, audit_id),
    UNIQUE (tenant_id, aggregate_type, aggregate_id, audit_id)
);

CREATE INDEX idx_evaluation_dataset_draft_sample_tenant_draft
    ON evaluation_dataset_draft_sample (tenant_id, draft_id);
CREATE INDEX idx_evaluation_run_manifest_tenant_dataset
    ON evaluation_run_manifest (tenant_id, dataset_version_id);
CREATE INDEX idx_evaluation_metric_evidence_tenant_run
    ON evaluation_metric_evidence (tenant_id, run_id);
CREATE INDEX idx_evaluation_dashscope_approval_tenant_run
    ON evaluation_dashscope_approval (tenant_id, run_id);
CREATE INDEX idx_evaluation_budget_ledger_tenant_run
    ON evaluation_budget_ledger (tenant_id, run_id);
CREATE INDEX idx_evaluation_gate_proof_tenant_decision
    ON evaluation_gate_proof (tenant_id, decision_id);
CREATE INDEX idx_evaluation_audit_tenant_aggregate
    ON evaluation_audit (tenant_id, aggregate_type, aggregate_id);
