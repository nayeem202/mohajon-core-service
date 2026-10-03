CREATE TABLE IF NOT EXISTS sale_returns (
    return_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(60) NOT NULL,
    branch_id VARCHAR(80) NOT NULL,
    sale_id VARCHAR(36) NOT NULL REFERENCES sales (sale_id),
    customer_id VARCHAR(36) NOT NULL,
    refund_amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    refund_method VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    staff_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_sale_returns_shop_updated ON sale_returns (shop_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_sale_returns_sale ON sale_returns (shop_id, sale_id);

CREATE TABLE IF NOT EXISTS sale_return_items (
    return_item_id VARCHAR(36) PRIMARY KEY,
    return_id VARCHAR(36) NOT NULL REFERENCES sale_returns (return_id) ON DELETE CASCADE,
    sale_item_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36),
    name VARCHAR(200) NOT NULL,
    qty DOUBLE PRECISION NOT NULL CHECK (qty > 0),
    unit_price DOUBLE PRECISION NOT NULL,
    purchase_price DOUBLE PRECISION NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sale_return_items_return ON sale_return_items (return_id);
