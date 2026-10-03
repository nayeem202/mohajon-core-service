ALTER TABLE expenses ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE expenses SET branch_id = shop_id || '-main' WHERE branch_id IS NULL OR branch_id = '';
ALTER TABLE expenses ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_expenses_shop_branch ON expenses(shop_id, branch_id, date);
