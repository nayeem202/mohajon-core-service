ALTER TABLE sales ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(40);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_shop_invoice_number ON sales(shop_id, invoice_number) WHERE invoice_number IS NOT NULL;
