-- A dedicated brand asset for invoices and reports.  Keep it separate from
-- the shop/profile image so a business can use a clean print-ready logo.
ALTER TABLE shops ADD COLUMN IF NOT EXISTS report_logo_path VARCHAR(500);
