ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS received_qty_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_purchase_order_shop_updated ON purchase_order(shop_id, updated_at);
