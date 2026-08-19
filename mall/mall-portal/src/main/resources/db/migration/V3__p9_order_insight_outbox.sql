CREATE TABLE mall_insight_outbox (
  event_id CHAR(36) NOT NULL,
  order_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  trace_id VARCHAR(128) NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  order_occurred_at TIMESTAMP(6) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL,
  published_at TIMESTAMP(6) NULL,
  last_error VARCHAR(256) NULL,
  PRIMARY KEY (event_id),
  UNIQUE KEY ux_mall_insight_paid_order (order_id, event_type),
  KEY idx_mall_insight_outbox_due (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
