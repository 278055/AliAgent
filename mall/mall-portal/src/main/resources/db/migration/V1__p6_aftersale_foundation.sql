CREATE TABLE after_sale_case (
  id BIGINT NOT NULL AUTO_INCREMENT,
  case_no VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  order_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  case_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  refund_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
  requested_amount DECIMAL(19,2) NOT NULL,
  approved_amount DECIMAL(19,2) NULL,
  rule_version_id VARCHAR(64) NOT NULL,
  authorization_snapshot_id CHAR(36) NOT NULL,
  command_id CHAR(36) NOT NULL,
  command_type VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  trace_id VARCHAR(128) NOT NULL,
  request_id CHAR(36) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY ux_after_sale_case_no (case_no),
  UNIQUE KEY ux_after_sale_case_command (command_id),
  UNIQUE KEY ux_after_sale_case_idempotency (tenant_id, command_type, idempotency_key),
  KEY idx_after_sale_case_order (tenant_id, order_id, status),
  KEY idx_after_sale_case_member (tenant_id, member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE after_sale_case_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  case_id BIGINT NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  case_status VARCHAR(32) NOT NULL,
  active_order_item_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN case_status IN ('DRAFT', 'WAITING_USER_CONFIRMATION', 'SUBMITTED', 'WAITING_STAFF_APPROVAL', 'WAITING_SUPERVISOR_APPROVAL', 'APPROVED', 'EXECUTING', 'RETRY_PENDING', 'MANUAL_RECONCILIATION') THEN order_item_id ELSE NULL END
  ) STORED,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY ux_after_sale_active_order_item (tenant_id, active_order_item_id),
  UNIQUE KEY ux_after_sale_case_item (case_id, order_item_id),
  KEY idx_after_sale_case_item_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE after_sale_saga (
  id BIGINT NOT NULL AUTO_INCREMENT,
  case_id BIGINT NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  current_step VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMP(6) NULL,
  last_error VARCHAR(1024) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY ux_after_sale_saga_case (case_id),
  KEY idx_after_sale_saga_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE after_sale_saga_step (
  id BIGINT NOT NULL AUTO_INCREMENT,
  saga_id BIGINT NOT NULL,
  step_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  started_at TIMESTAMP(6) NULL,
  completed_at TIMESTAMP(6) NULL,
  error_message VARCHAR(1024) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY ux_after_sale_saga_step (saga_id, step_type),
  UNIQUE KEY ux_after_sale_saga_step_idempotency (saga_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE after_sale_approval (
  id BIGINT NOT NULL AUTO_INCREMENT,
  case_id BIGINT NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  approval_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  approver_id VARCHAR(128) NULL,
  authorization_snapshot_id CHAR(36) NOT NULL,
  decision_note VARCHAR(1024) NULL,
  decided_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY ux_after_sale_approval_stage (case_id, approval_type),
  KEY idx_after_sale_approval_queue (tenant_id, approval_type, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE after_sale_outbox (
  event_id CHAR(36) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_version INT NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  trace_id VARCHAR(128) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL,
  published_at TIMESTAMP(6) NULL,
  last_error TEXT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_after_sale_outbox_due (status, next_attempt_at),
  KEY idx_after_sale_outbox_aggregate (aggregate_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE after_sale_inbox (
  event_id CHAR(36) NOT NULL,
  consumer_name VARCHAR(100) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  PRIMARY KEY (event_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE after_sale_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  case_id BIGINT NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  actor_id VARCHAR(128) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  authorization_snapshot_id CHAR(36) NOT NULL,
  command_id CHAR(36) NULL,
  trace_id VARCHAR(128) NOT NULL,
  request_id CHAR(36) NOT NULL,
  detail JSON NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_after_sale_audit_case (case_id, occurred_at),
  KEY idx_after_sale_audit_tenant (tenant_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
