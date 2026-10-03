ALTER TABLE guardian_audit_event ADD COLUMN IF NOT EXISTS branch_id VARCHAR(80);
UPDATE guardian_audit_event SET branch_id = shop_id || '-main' WHERE branch_id IS NULL OR branch_id = '';
ALTER TABLE guardian_audit_event ALTER COLUMN branch_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_guardian_audit_shop_branch ON guardian_audit_event(shop_id, branch_id, created_at);
