ALTER TABLE evaluation_dataset
    ADD CONSTRAINT uq_evaluation_dataset_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE evaluation_dataset_version
    ADD CONSTRAINT uq_evaluation_dataset_version_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE evaluation_gate_decision
    ADD CONSTRAINT uq_evaluation_gate_decision_tenant_id UNIQUE (tenant_id, id);
