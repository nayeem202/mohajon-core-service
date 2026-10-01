-- =============================================================================
-- PRODUCT EXPIRY DATE (stock expiry tracking)
-- Date-only (LocalDate) so a product can expire regardless of the time it was
-- saved. May be NULL for products that never expire (e.g. non-perishables).
-- =============================================================================

ALTER TABLE products ADD COLUMN expiry_date date;