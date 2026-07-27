ALTER TABLE evaluation_dataset_draft_sample
    ALTER COLUMN tenant_id TYPE VARCHAR(128),
    ADD COLUMN dataset_id UUID,
    ADD CONSTRAINT fk_evaluation_draft_sample_tenant_dataset
        FOREIGN KEY (tenant_id, dataset_id) REFERENCES evaluation_dataset (tenant_id, id);

ALTER TABLE evaluation_run_manifest
    ALTER COLUMN tenant_id TYPE VARCHAR(128),
    ADD COLUMN dataset_version_id UUID,
    ADD CONSTRAINT fk_evaluation_run_manifest_tenant_run
        FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id),
    ADD CONSTRAINT fk_evaluation_run_manifest_tenant_dataset_version
        FOREIGN KEY (tenant_id, dataset_version_id) REFERENCES evaluation_dataset_version (tenant_id, id);

UPDATE evaluation_run_manifest manifest
SET dataset_version_id = run.dataset_version_id
FROM evaluation_run run
WHERE manifest.tenant_id = run.tenant_id
  AND manifest.run_id = run.id;

ALTER TABLE evaluation_run_manifest
    ALTER COLUMN dataset_version_id SET NOT NULL,
    DROP COLUMN dataset_id;

ALTER TABLE evaluation_metric_evidence
    ALTER COLUMN tenant_id TYPE VARCHAR(128),
    ADD CONSTRAINT fk_evaluation_metric_evidence_tenant_run
        FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id);

ALTER TABLE evaluation_dashscope_approval
    ALTER COLUMN tenant_id TYPE VARCHAR(128),
    ADD CONSTRAINT fk_evaluation_dashscope_approval_tenant_run
        FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id);

ALTER TABLE evaluation_budget_ledger
    ALTER COLUMN tenant_id TYPE VARCHAR(128),
    ADD CONSTRAINT fk_evaluation_budget_ledger_tenant_run
        FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id);

ALTER TABLE evaluation_gate_proof
    ALTER COLUMN tenant_id TYPE VARCHAR(128),
    ADD CONSTRAINT fk_evaluation_gate_proof_tenant_decision
        FOREIGN KEY (tenant_id, decision_id) REFERENCES evaluation_gate_decision (tenant_id, id);

ALTER TABLE evaluation_audit
    ALTER COLUMN tenant_id TYPE VARCHAR(128);
