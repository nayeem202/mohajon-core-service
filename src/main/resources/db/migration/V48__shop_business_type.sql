ALTER TABLE shops ADD COLUMN IF NOT EXISTS business_type VARCHAR(64);

UPDATE shops
SET business_type = 'general'
WHERE business_type IS NULL OR TRIM(business_type) = '';
