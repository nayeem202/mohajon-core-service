ALTER TABLE sales ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);

UPDATE sales SET branch_id = shop_id || '-main' WHERE branch_id IS NULL;
UPDATE payments SET branch_id = shop_id || '-main' WHERE branch_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_sales_shop_branch_created
    ON sales (shop_id, branch_id, created_at);
CREATE INDEX IF NOT EXISTS idx_payments_shop_branch_created
    ON payments (shop_id, branch_id, created_at);
