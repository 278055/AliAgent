CREATE TABLE order_tenant_binding (
  order_id BIGINT NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  source VARCHAR(32) NOT NULL,
  migration_batch_id VARCHAR(64) NULL,
  evidence_reference VARCHAR(256) NULL,
  bound_by VARCHAR(128) NOT NULL,
  bound_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (order_id),
  KEY idx_order_tenant_binding_tenant (tenant_id, order_id),
  CONSTRAINT chk_order_tenant_binding_source CHECK (
    source IN ('ORDER_CREATION', 'HISTORICAL_BACKFILL')
  ),
  CONSTRAINT chk_order_tenant_binding_backfill_metadata CHECK (
    (source = 'ORDER_CREATION' AND migration_batch_id IS NULL AND evidence_reference IS NULL)
    OR (source = 'HISTORICAL_BACKFILL' AND migration_batch_id IS NOT NULL AND evidence_reference IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
