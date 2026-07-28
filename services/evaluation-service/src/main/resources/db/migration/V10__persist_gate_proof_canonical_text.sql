ALTER TABLE evaluation_gate_proof ADD COLUMN canonical_payload_text TEXT;
UPDATE evaluation_gate_proof SET canonical_payload_text = canonical_payload #>> '{}' WHERE canonical_payload_text IS NULL;
ALTER TABLE evaluation_gate_proof ALTER COLUMN canonical_payload_text SET NOT NULL;
