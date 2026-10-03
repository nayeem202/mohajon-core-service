ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE purchase_order SET branch_id = shop_id || '-main' WHERE branch_id IS NULL OR branch_id = '';
ALTER TABLE purchase_order ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_purchase_order_shop_branch ON purchase_order(shop_id, branch_id, created_at);
