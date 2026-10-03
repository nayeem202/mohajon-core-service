ALTER TABLE customers ADD COLUMN IF NOT EXISTS credit_limit DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE products ADD COLUMN IF NOT EXISTS sku VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS variant_name VARCHAR(120);
ALTER TABLE products ADD COLUMN IF NOT EXISTS batch_number VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_products_shop_sku ON products (shop_id, sku);
CREATE INDEX IF NOT EXISTS idx_products_shop_batch ON products (shop_id, batch_number);
