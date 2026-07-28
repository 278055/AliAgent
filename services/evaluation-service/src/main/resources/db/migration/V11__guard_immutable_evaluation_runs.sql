CREATE OR REPLACE FUNCTION guard_evaluation_run_manifest_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'evaluation run manifest is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evaluation_run_manifest_immutable
BEFORE UPDATE OR DELETE ON evaluation_run_manifest
FOR EACH ROW EXECUTE FUNCTION guard_evaluation_run_manifest_immutable();

CREATE OR REPLACE FUNCTION guard_evaluation_run_manifest_digest_immutable() RETURNS trigger AS $$
BEGIN
    IF NEW.manifest_digest <> OLD.manifest_digest THEN RAISE EXCEPTION 'evaluation run manifest digest is immutable'; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evaluation_run_manifest_digest_immutable
BEFORE UPDATE ON evaluation_run
FOR EACH ROW EXECUTE FUNCTION guard_evaluation_run_manifest_digest_immutable();
