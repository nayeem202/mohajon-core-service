-- =============================================================================
-- SUBSCRIPTIONS & bKash PAYMENT TRANSACTIONS
-- Plans are configured in the DB only (prices are never hardcoded in code).
-- =============================================================================

CREATE TABLE subscription_plan (
    plan_code      VARCHAR(30) PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    price_bdt      NUMERIC(12, 2) NOT NULL,
    duration_days  INT NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL
);

CREATE TABLE subscription (
    subscription_id   VARCHAR(36) PRIMARY KEY,
    shop_id           VARCHAR(60) NOT NULL,
    plan_code         VARCHAR(30) NOT NULL REFERENCES subscription_plan (plan_code),
    status            VARCHAR(20) NOT NULL,
    amount_bdt        NUMERIC(12, 2) NOT NULL,
    start_date        TIMESTAMP,
    end_date          TIMESTAMP,
    internal_transaction_id VARCHAR(64) NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    CONSTRAINT uk_subscription_internal_transaction UNIQUE (internal_transaction_id)
);
CREATE INDEX idx_subscription_shop_status ON subscription (shop_id, status);
CREATE INDEX idx_subscription_shop_end_date ON subscription (shop_id, end_date);

CREATE TABLE payment_transaction (
    payment_id           VARCHAR(36) PRIMARY KEY,
    subscription_id      VARCHAR(36) NOT NULL REFERENCES subscription (subscription_id),
    shop_id              VARCHAR(60) NOT NULL,
    internal_transaction_id VARCHAR(64) NOT NULL,
    gateway_payment_id   VARCHAR(128),
    gateway_transaction_id VARCHAR(128),
    gateway              VARCHAR(20) NOT NULL DEFAULT 'BKASH',
    amount_bdt           NUMERIC(12, 2) NOT NULL,
    status               VARCHAR(20) NOT NULL,
    payment_create_time  TIMESTAMP,
    execute_time         TIMESTAMP,
    paid_time            TIMESTAMP,
    payer_reference      VARCHAR(60),
    merchant_invoice_number VARCHAR(64),
    billing_type         VARCHAR(20),
    error_code           VARCHAR(30),
    error_message        VARCHAR(300),
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP NOT NULL,
    CONSTRAINT uk_payment_transaction_internal UNIQUE (internal_transaction_id),
    CONSTRAINT uk_payment_transaction_gateway_payment UNIQUE (gateway_payment_id),
    CONSTRAINT uk_payment_transaction_gateway_trx UNIQUE (gateway_transaction_id)
);
CREATE INDEX idx_payment_transaction_shop ON payment_transaction (shop_id);
CREATE INDEX idx_payment_transaction_status ON payment_transaction (status);
CREATE INDEX idx_payment_transaction_subscription ON payment_transaction (subscription_id);

-- ---------------------------------------------------------------------
-- SEED PLANS (prices are editable in the DB at runtime)
-- ---------------------------------------------------------------------

INSERT INTO subscription_plan (plan_code, name, price_bdt, duration_days, is_active, sort_order, created_at, updated_at) VALUES
    ('MONTHLY',    'Monthly',    499.00,  30, TRUE, 1, NOW(), NOW()),
    ('SIX_MONTHS', '6 Months',   2499.00, 180, TRUE, 2, NOW(), NOW()),
    ('YEARLY',     'Yearly',     4499.00, 365, TRUE, 3, NOW(), NOW());