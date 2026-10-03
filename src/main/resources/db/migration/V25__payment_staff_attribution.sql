ALTER TABLE payments ADD COLUMN IF NOT EXISTS staff_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_payments_staff ON payments(shop_id, staff_id, created_at);
