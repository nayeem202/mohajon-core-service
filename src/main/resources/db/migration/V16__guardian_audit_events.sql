CREATE TABLE guardian_audit_event (
    event_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(60) NOT NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(80),
    amount DOUBLE PRECISION,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_guardian_audit_shop_date ON guardian_audit_event (shop_id, created_at DESC);
CREATE INDEX idx_guardian_audit_actor ON guardian_audit_event (shop_id, actor_user_id, created_at DESC);
