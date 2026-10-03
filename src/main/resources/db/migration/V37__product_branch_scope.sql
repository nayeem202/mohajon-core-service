ALTER TABLE products ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE products SET branch_id = shop_id || '-main' WHERE branch_id IS NULL OR branch_id = '';
ALTER TABLE products ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_products_shop_branch ON products(shop_id, branch_id, is_deleted);
