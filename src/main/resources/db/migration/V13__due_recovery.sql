CREATE TABLE due_recovery_plan (
    id                    VARCHAR(36) PRIMARY KEY,
    shop_id               VARCHAR(60) NOT NULL,
    customer_id           VARCHAR(36) NOT NULL,
    promised_payment_date DATE,
    snoozed_until         TIMESTAMP,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    CONSTRAINT uk_due_recovery_shop_customer UNIQUE (shop_id, customer_id)
);

CREATE INDEX idx_due_recovery_shop_updated
    ON due_recovery_plan (shop_id, updated_at);

CREATE TABLE due_recovery_follow_up (
    follow_up_id VARCHAR(36) PRIMARY KEY,
    shop_id      VARCHAR(60) NOT NULL,
    customer_id  VARCHAR(36) NOT NULL,
    channel      VARCHAR(20) NOT NULL,
    note         VARCHAR(500),
    created_at   TIMESTAMP NOT NULL
);

CREATE INDEX idx_due_recovery_follow_up_customer
    ON due_recovery_follow_up (shop_id, customer_id, created_at);
