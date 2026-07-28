CREATE OR REPLACE FUNCTION guard_evaluation_result_lifecycle() RETURNS trigger AS $$
DECLARE
    value_tenant VARCHAR(128);
    value_run UUID;
BEGIN
    value_tenant := COALESCE(NEW.tenant_id, OLD.tenant_id);
    value_run := COALESCE(NEW.run_id, OLD.run_id);
    PERFORM 1 FROM evaluation_run WHERE tenant_id = value_tenant AND id = value_run AND status = 'RUNNING' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'evaluation result lifecycle requires a running evaluation run'; END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evaluation_result_lifecycle
BEFORE INSERT OR UPDATE OR DELETE ON evaluation_result
FOR EACH ROW EXECUTE FUNCTION guard_evaluation_result_lifecycle();

CREATE OR REPLACE FUNCTION guard_evaluation_evidence_lifecycle() RETURNS trigger AS $$
DECLARE
    value_tenant VARCHAR(128);
    value_run UUID;
BEGIN
    value_tenant := COALESCE(NEW.tenant_id, OLD.tenant_id);
    value_run := COALESCE(NEW.run_id, OLD.run_id);
    PERFORM 1 FROM evaluation_run WHERE tenant_id = value_tenant AND id = value_run AND status = 'RUNNING' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'evaluation evidence lifecycle requires a running evaluation run'; END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evaluation_evidence_lifecycle
BEFORE INSERT OR UPDATE OR DELETE ON evaluation_metric_evidence
FOR EACH ROW EXECUTE FUNCTION guard_evaluation_evidence_lifecycle();
