UPDATE evaluation_sample_snapshot snapshot
SET candidate_id = matched.candidate_id
FROM (
    SELECT snapshot.id, (ARRAY_AGG(member.sample_id ORDER BY member.sample_id::text))[1] AS candidate_id
    FROM evaluation_sample_snapshot snapshot
    JOIN evaluation_dataset_version version ON version.id = snapshot.dataset_version_id AND version.tenant_id = snapshot.tenant_id
    JOIN evaluation_dataset_draft_sample member ON member.dataset_id = version.dataset_id AND member.tenant_id = snapshot.tenant_id
    WHERE snapshot.candidate_id IS NULL
    GROUP BY snapshot.id
    HAVING COUNT(*) = 1
) matched
WHERE snapshot.id = matched.id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM evaluation_sample_snapshot WHERE candidate_id IS NULL) THEN
        RAISE EXCEPTION 'cannot uniquely backfill evaluation_sample_snapshot.candidate_id; manual migration mapping is required';
    END IF;
END $$;

ALTER TABLE evaluation_sample_snapshot ALTER COLUMN candidate_id SET NOT NULL;
ALTER TABLE evaluation_sample_snapshot
    ADD CONSTRAINT fk_evaluation_snapshot_tenant_candidate FOREIGN KEY (tenant_id, candidate_id)
    REFERENCES evaluation_candidate (tenant_id, id);
