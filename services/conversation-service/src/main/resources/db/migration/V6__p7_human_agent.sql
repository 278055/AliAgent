CREATE TABLE agent_skill_group (
    id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, name VARCHAR(128) NOT NULL,
    routing_rule_version VARCHAR(64) NOT NULL, max_assignment_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_assignment_attempts > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (tenant_id, name)
);
CREATE TABLE agent_skill_tag (id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, code VARCHAR(64) NOT NULL, UNIQUE (tenant_id, code));
CREATE TABLE agent_skill_group_tag (tenant_id VARCHAR(128) NOT NULL, skill_group_id UUID NOT NULL REFERENCES agent_skill_group(id), tag_id UUID NOT NULL REFERENCES agent_skill_tag(id), PRIMARY KEY (tenant_id, skill_group_id, tag_id));
CREATE TABLE agent_skill_group_member (
    tenant_id VARCHAR(128) NOT NULL, skill_group_id UUID NOT NULL REFERENCES agent_skill_group(id), staff_id VARCHAR(128) NOT NULL,
    max_concurrent INTEGER NOT NULL CHECK (max_concurrent > 0), enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_assigned_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (tenant_id, skill_group_id, staff_id)
);
CREATE TABLE human_queue_item (
    id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, conversation_id UUID NOT NULL REFERENCES conversation(id), skill_group_id UUID NOT NULL REFERENCES agent_skill_group(id),
    priority INTEGER NOT NULL, routing_rule_version VARCHAR(64) NOT NULL, priority_rule_version VARCHAR(64) NOT NULL,
    request_id UUID NOT NULL, assignment_attempts INTEGER NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL CHECK (status IN ('WAITING','OFFERED','CLAIMABLE','ASSIGNED','CANCELLED','CLOSED')),
    enqueued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, version BIGINT NOT NULL DEFAULT 0, UNIQUE (tenant_id, request_id)
);
CREATE UNIQUE INDEX ux_human_queue_active ON human_queue_item(tenant_id, conversation_id) WHERE status IN ('WAITING','OFFERED','CLAIMABLE');
CREATE INDEX idx_human_queue_assignment ON human_queue_item(tenant_id, skill_group_id, status, priority DESC, enqueued_at);
CREATE TABLE human_assignment_offer (
    id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, queue_item_id UUID NOT NULL REFERENCES human_queue_item(id), conversation_id UUID NOT NULL REFERENCES conversation(id), staff_id VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL, status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','CANCELLED')),
    takeover_id UUID, request_id UUID NOT NULL, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (tenant_id, request_id)
);
CREATE UNIQUE INDEX ux_human_offer_pending ON human_assignment_offer(tenant_id, conversation_id) WHERE status = 'PENDING';
CREATE INDEX idx_human_offer_expiry ON human_assignment_offer(status, expires_at);
CREATE TABLE human_takeover (
    id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, conversation_id UUID NOT NULL REFERENCES conversation(id), staff_id VARCHAR(128) NOT NULL,
    source_offer_id UUID, request_id UUID NOT NULL, status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE','RELEASED','TRANSFERRED','CLOSED','FORCE_RELEASED')),
    reason TEXT, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, ended_at TIMESTAMPTZ, UNIQUE (tenant_id, request_id)
);
CREATE UNIQUE INDEX ux_human_takeover_active ON human_takeover(tenant_id, conversation_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_human_takeover_capacity ON human_takeover(tenant_id, staff_id) WHERE status = 'ACTIVE';
CREATE TABLE human_transfer (
    id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, conversation_id UUID NOT NULL REFERENCES conversation(id), source_staff_id VARCHAR(128) NOT NULL,
    target_staff_id VARCHAR(128), target_skill_group_id UUID REFERENCES agent_skill_group(id), request_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','CANCELLED')), reason TEXT,
    version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (tenant_id, request_id)
);
CREATE TABLE human_collaboration_outbox (id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, aggregate_id UUID NOT NULL, request_id UUID NOT NULL, topic VARCHAR(128) NOT NULL, event_type VARCHAR(128) NOT NULL, event_version INTEGER NOT NULL, payload JSONB NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (tenant_id, request_id, event_type));
CREATE TABLE human_collaboration_inbox (event_id UUID NOT NULL, consumer_name VARCHAR(100) NOT NULL, tenant_id VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMPTZ, PRIMARY KEY (event_id, consumer_name));
CREATE TABLE human_collaboration_audit (id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, conversation_id UUID, actor_id VARCHAR(128), action VARCHAR(64) NOT NULL, reason TEXT, request_id UUID, payload JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE conversation_human_state ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversation_human_state ADD CONSTRAINT ck_conversation_human_state_p7 CHECK (status IN ('AI_ACTIVE','WAITING_HUMAN','ASSIGNING','HUMAN_ACTIVE','CLOSED'));
ALTER TABLE message ADD COLUMN IF NOT EXISTS client_message_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS ux_message_staff_client ON message(tenant_id, conversation_id, client_message_id) WHERE client_message_id IS NOT NULL;
