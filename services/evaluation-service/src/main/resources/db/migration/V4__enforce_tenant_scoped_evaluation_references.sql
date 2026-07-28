ALTER TABLE evaluation_candidate
    ADD CONSTRAINT uq_evaluation_candidate_tenant_id UNIQUE (tenant_id, id);

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN
        SELECT tc.constraint_name, tc.table_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_schema = kcu.constraint_schema
         AND tc.constraint_name = kcu.constraint_name
        WHERE tc.constraint_schema = current_schema()
          AND tc.constraint_type = 'FOREIGN KEY'
          AND ((tc.table_name = 'evaluation_candidate_review' AND kcu.column_name = 'candidate_id')
            OR (tc.table_name = 'evaluation_dataset_version' AND kcu.column_name = 'dataset_id')
            OR (tc.table_name = 'evaluation_sample_snapshot' AND kcu.column_name = 'dataset_version_id')
            OR (tc.table_name = 'evaluation_result' AND kcu.column_name = 'run_id'))
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', foreign_key.table_name, foreign_key.constraint_name);
    END LOOP;
END $$;

ALTER TABLE evaluation_candidate_review
    ADD CONSTRAINT fk_evaluation_candidate_review_tenant_candidate
        FOREIGN KEY (tenant_id, candidate_id) REFERENCES evaluation_candidate (tenant_id, id);
ALTER TABLE evaluation_dataset_version
    ADD CONSTRAINT fk_evaluation_dataset_version_tenant_dataset
        FOREIGN KEY (tenant_id, dataset_id) REFERENCES evaluation_dataset (tenant_id, id);
ALTER TABLE evaluation_sample_snapshot
    ADD CONSTRAINT fk_evaluation_sample_snapshot_tenant_version
        FOREIGN KEY (tenant_id, dataset_version_id) REFERENCES evaluation_dataset_version (tenant_id, id);
ALTER TABLE evaluation_result
    ADD CONSTRAINT fk_evaluation_result_tenant_run
        FOREIGN KEY (tenant_id, run_id) REFERENCES evaluation_run (tenant_id, id);
ALTER TABLE evaluation_gate_revocation
    ADD CONSTRAINT fk_evaluation_gate_revocation_tenant_decision
        FOREIGN KEY (tenant_id, decision_id) REFERENCES evaluation_gate_decision (tenant_id, id);
