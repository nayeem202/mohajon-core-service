ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS batch_number VARCHAR(100);
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS expiry_date DATE;
ALTER TABLE purchase_items ADD COLUMN IF NOT EXISTS batch_number VARCHAR(100);
ALTER TABLE purchase_items ADD COLUMN IF NOT EXISTS expiry_date DATE;
