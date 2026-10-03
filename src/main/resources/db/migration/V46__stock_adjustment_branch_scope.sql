ALTER TABLE stock_adjustments ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE stock_adjustments SET branch_id = shop_id || '-main' WHERE branch_id IS NULL;
ALTER TABLE stock_adjustments ALTER COLUMN branch_id SET NOT NULL;
