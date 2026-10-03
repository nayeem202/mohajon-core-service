ALTER TABLE due_recovery_follow_up ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE due_recovery_follow_up SET updated_at = created_at WHERE updated_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_due_recovery_follow_up_updated ON due_recovery_follow_up(shop_id, updated_at);
