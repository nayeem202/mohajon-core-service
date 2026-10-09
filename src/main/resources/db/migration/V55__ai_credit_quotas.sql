ALTER TABLE subscription_plan
    ADD COLUMN IF NOT EXISTS ai_credits_per_month INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_welcome_credits INT NOT NULL DEFAULT 0;

UPDATE subscription_plan SET ai_credits_per_month = 0, ai_welcome_credits = 5 WHERE plan_code = 'FREE';
UPDATE subscription_plan SET ai_credits_per_month = 100, ai_welcome_credits = 0 WHERE plan_code = 'TRIAL';
UPDATE subscription_plan SET ai_credits_per_month = 150, ai_welcome_credits = 0
WHERE tier = 'PRO' OR plan_code IN ('MONTHLY', 'SIX_MONTHS', 'YEARLY');
UPDATE subscription_plan SET ai_credits_per_month = 500, ai_welcome_credits = 0
WHERE tier = 'BUSINESS' AND plan_code <> 'TRIAL';

CREATE TABLE ai_credit_balance (
    shop_id VARCHAR(60) PRIMARY KEY,
    welcome_credits_remaining INT NOT NULL DEFAULT 5,
    period_start DATE NOT NULL,
    monthly_credits INT NOT NULL DEFAULT 0,
    monthly_credits_used INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE ai_credit_usage (
    usage_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(60) NOT NULL,
    feature VARCHAR(40) NOT NULL,
    monthly_credits_used INT NOT NULL DEFAULT 0,
    welcome_credits_used INT NOT NULL DEFAULT 0,
    refunded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    refunded_at TIMESTAMP
);
CREATE INDEX idx_ai_credit_usage_shop_created ON ai_credit_usage (shop_id, created_at DESC);
