CREATE TABLE IF NOT EXISTS stock_adjustments (
    adjustment_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(60) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    approver_id VARCHAR(36),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_stock_adjustments_shop_updated ON stock_adjustments(shop_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_stock_adjustments_product ON stock_adjustments(shop_id, product_id, created_at);
