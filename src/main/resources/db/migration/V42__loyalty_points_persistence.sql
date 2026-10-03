ALTER TABLE customers ADD COLUMN IF NOT EXISTS loyalty_points INT NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS loyalty_points_redeemed INT NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS loyalty_points_earned INT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD CONSTRAINT customers_loyalty_points_nonnegative CHECK (loyalty_points >= 0);
ALTER TABLE sales ADD CONSTRAINT sales_loyalty_points_redeemed_nonnegative CHECK (loyalty_points_redeemed >= 0);
ALTER TABLE sales ADD CONSTRAINT sales_loyalty_points_earned_nonnegative CHECK (loyalty_points_earned >= 0);
