ALTER TABLE conversation_outbox
    ADD CONSTRAINT ux_conversation_outbox_request_version UNIQUE (tenant_id, conversation_id, request_id, event_version);
