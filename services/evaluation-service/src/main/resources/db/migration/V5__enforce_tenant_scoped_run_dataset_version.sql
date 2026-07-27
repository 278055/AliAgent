ALTER TABLE evaluation_run
    ADD CONSTRAINT fk_evaluation_run_tenant_dataset_version
        FOREIGN KEY (tenant_id, dataset_version_id) REFERENCES evaluation_dataset_version (tenant_id, id);
