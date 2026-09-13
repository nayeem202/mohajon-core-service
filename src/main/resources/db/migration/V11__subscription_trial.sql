-- =============================================================================
-- FREE TRIAL (configurable in DB) + 29 BDT/month pricing
-- The trial length lives in subscription_plan.trial_days (plan_code = 'TRIAL').
-- Change the number any time in the DB — new registrations pick it up
-- immediately without a server restart. Existing trials are never touched.
-- =============================================================================

ALTER TABLE subscription_plan ADD COLUMN trial_days INT;

-- Trial plan: NOT active (hidden from the purchasable plans list), price 0.
INSERT INTO subscription_plan (plan_code, name, price_bdt, duration_days, trial_days, is_active, sort_order, created_at, updated_at)
VALUES ('TRIAL', 'Free Trial', 0.00, 30, 30, FALSE, 99, NOW(), NOW())
ON CONFLICT (plan_code) DO UPDATE
    SET trial_days = EXCLUDED.trial_days,
        updated_at = NOW();

-- 29 BDT/month pricing (editable in the DB at any time).
UPDATE subscription_plan SET price_bdt = 29.00, updated_at = NOW() WHERE plan_code = 'MONTHLY';
UPDATE subscription_plan SET price_bdt = 174.00, updated_at = NOW() WHERE plan_code = 'SIX_MONTHS';
UPDATE subscription_plan SET price_bdt = 348.00, updated_at = NOW() WHERE plan_code = 'YEARLY';
