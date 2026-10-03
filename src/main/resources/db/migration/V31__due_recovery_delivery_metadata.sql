ALTER TABLE due_recovery_follow_up ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP;
ALTER TABLE due_recovery_follow_up ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);
ALTER TABLE due_recovery_follow_up ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(200);

UPDATE due_recovery_follow_up
SET status_changed_at = COALESCE(updated_at, created_at)
WHERE status_changed_at IS NULL;
