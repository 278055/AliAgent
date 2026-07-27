ALTER TABLE evaluation_candidate
    ADD COLUMN review_expected JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN review_labels JSONB NOT NULL DEFAULT '[]'::jsonb;
