ALTER TABLE sales ADD COLUMN IF NOT EXISTS discount_staff_id VARCHAR(36);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS voided_by_staff_id VARCHAR(36);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_sales_shop_discount_staff
    ON sales (shop_id, discount_staff_id);
CREATE INDEX IF NOT EXISTS idx_sales_shop_voided_staff
    ON sales (shop_id, voided_by_staff_id);
