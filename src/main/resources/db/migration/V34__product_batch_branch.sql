ALTER TABLE product_batches ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE product_batches SET branch_id = shop_id || '-main' WHERE branch_id IS NULL;
ALTER TABLE product_batches ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_product_batches_branch_product_expiry
    ON product_batches (shop_id, branch_id, product_id, expiry_date);
