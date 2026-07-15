CREATE TABLE IF NOT EXISTS outbox_event (
  event_id CHAR(36) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_version INT NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  trace_id VARCHAR(128) NOT NULL,
  producer VARCHAR(128) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL,
  published_at TIMESTAMP(6) NULL,
  last_error TEXT NULL,
  PRIMARY KEY (event_id),
  KEY idx_outbox_due (status, next_attempt_at),
  KEY idx_outbox_type_time (event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
