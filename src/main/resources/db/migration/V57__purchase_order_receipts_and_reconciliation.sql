ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS delivered_qty_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS receipts_json TEXT NOT NULL DEFAULT '[]';

UPDATE purchase_order
SET delivered_qty_json = received_qty_json
WHERE delivered_qty_json IS NULL OR delivered_qty_json = '{}';
