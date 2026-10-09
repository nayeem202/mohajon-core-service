-- Permanent Free tier, full-feature trial, and paid Pro/Business plans.
-- Existing paid subscriptions remain Pro through the default tier below.
ALTER TABLE subscription_plan
    ADD COLUMN IF NOT EXISTS tier VARCHAR(20) NOT NULL DEFAULT 'PRO',
    ADD COLUMN IF NOT EXISTS description VARCHAR(300);

UPDATE subscription_plan
SET tier = CASE
        WHEN plan_code = 'TRIAL' THEN 'BUSINESS'
        WHEN plan_code = 'FREE' THEN 'FREE'
        ELSE 'PRO'
    END,
    updated_at = NOW();

INSERT INTO subscription_plan
    (plan_code, name, price_bdt, duration_days, trial_days, tier, description, is_active, sort_order, created_at, updated_at)
VALUES
    ('FREE', 'Free', 0.00, 36500, NULL, 'FREE', 'Core POS, sales, customers, products and expenses.', FALSE, 0, NOW(), NOW()),
    ('PRO_MONTHLY', 'Pro Monthly', 249.00, 30, NULL, 'PRO', 'Reports, exports, purchase tools, vendors, investments and AI.', TRUE, 10, NOW(), NOW()),
    ('PRO_YEARLY', 'Pro Yearly', 2490.00, 365, NULL, 'PRO', 'All Pro features. Pay for 10 months and use it for 12.', TRUE, 11, NOW(), NOW()),
    ('BUSINESS_MONTHLY', 'Business Monthly', 699.00, 30, NULL, 'BUSINESS', 'Pro plus staff, roles, audit trail and multi-branch.', TRUE, 20, NOW(), NOW()),
    ('BUSINESS_YEARLY', 'Business Yearly', 6990.00, 365, NULL, 'BUSINESS', 'All Business features. Pay for 10 months and use it for 12.', TRUE, 21, NOW(), NOW())
ON CONFLICT (plan_code) DO UPDATE
SET name = EXCLUDED.name,
    price_bdt = EXCLUDED.price_bdt,
    duration_days = EXCLUDED.duration_days,
    tier = EXCLUDED.tier,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();

-- These plans are retained for subscription history and active customers but
-- are no longer sold. Their tier remains Pro.
UPDATE subscription_plan
SET is_active = FALSE, tier = 'PRO', updated_at = NOW()
WHERE plan_code IN ('MONTHLY', 'SIX_MONTHS', 'YEARLY');
