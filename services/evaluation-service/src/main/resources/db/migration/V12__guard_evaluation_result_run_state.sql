CREATE OR REPLACE FUNCTION guard_evaluation_result_run_state() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM evaluation_run WHERE tenant_id = NEW.tenant_id AND id = NEW.run_id AND status = 'RUNNING') THEN
        RAISE EXCEPTION 'evaluation results require a running evaluation run';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evaluation_result_run_state
BEFORE INSERT ON evaluation_result
FOR EACH ROW EXECUTE FUNCTION guard_evaluation_result_run_state();

CREATE OR REPLACE FUNCTION guard_evaluation_evidence_run_state() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM evaluation_run WHERE tenant_id = NEW.tenant_id AND id = NEW.run_id AND status = 'RUNNING') THEN
        RAISE EXCEPTION 'evaluation evidence requires a running evaluation run';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evaluation_evidence_run_state
BEFORE INSERT ON evaluation_metric_evidence
FOR EACH ROW EXECUTE FUNCTION guard_evaluation_evidence_run_state();
