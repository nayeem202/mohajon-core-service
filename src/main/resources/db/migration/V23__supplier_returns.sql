CREATE TABLE IF NOT EXISTS supplier_returns (
    return_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(60) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36),
    items_json TEXT NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_supplier_returns_shop_updated ON supplier_returns(shop_id, updated_at);
