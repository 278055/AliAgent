CREATE OR REPLACE FUNCTION guard_immutable_evaluation_sample_snapshot() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'published evaluation sample snapshots are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_immutable_evaluation_sample_snapshot
BEFORE UPDATE OR DELETE ON evaluation_sample_snapshot
FOR EACH ROW EXECUTE FUNCTION guard_immutable_evaluation_sample_snapshot();
