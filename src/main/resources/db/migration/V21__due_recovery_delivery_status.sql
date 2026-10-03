ALTER TABLE due_recovery_follow_up ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'opened';
