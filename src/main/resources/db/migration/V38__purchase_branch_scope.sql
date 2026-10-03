ALTER TABLE purchases ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE purchases SET branch_id = shop_id || '-main' WHERE branch_id IS NULL OR branch_id = '';
ALTER TABLE purchases ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_purchases_shop_branch ON purchases(shop_id, branch_id, is_deleted, created_at);
