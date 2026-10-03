ALTER TABLE supplier_returns ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE supplier_returns SET branch_id = shop_id || '-main' WHERE branch_id IS NULL OR branch_id = '';
ALTER TABLE supplier_returns ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_supplier_returns_shop_branch ON supplier_returns(shop_id, branch_id, updated_at);
