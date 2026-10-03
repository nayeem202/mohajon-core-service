CREATE TABLE IF NOT EXISTS product_batches (
    batch_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(60) NOT NULL,
    branch_id VARCHAR(80) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    batch_number VARCHAR(100) NOT NULL,
    expiry_date DATE,
    quantity INTEGER NOT NULL DEFAULT 0,
    unit_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_product_batches_shop_product_expiry
    ON product_batches (shop_id, branch_id, product_id, expiry_date);
CREATE INDEX IF NOT EXISTS idx_product_batches_shop_updated
    ON product_batches (shop_id, updated_at);
