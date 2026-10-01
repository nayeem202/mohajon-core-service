ALTER TABLE sales ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'CASH';
ALTER TABLE sales ADD COLUMN IF NOT EXISTS staff_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_sales_staff ON sales (shop_id, staff_id, created_at);
