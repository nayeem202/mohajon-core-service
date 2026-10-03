ALTER TABLE business_coach_insight ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE business_coach_insight SET branch_id = shop_id || '-main' WHERE branch_id IS NULL OR branch_id = '';
ALTER TABLE business_coach_insight ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_coach_insight_shop_branch ON business_coach_insight(shop_id, branch_id, created_at);
