ALTER TABLE evaluation_sample_snapshot
    ADD COLUMN candidate_id UUID,
    ADD COLUMN snapshot_payload JSONB NOT NULL DEFAULT '{}'::jsonb;
