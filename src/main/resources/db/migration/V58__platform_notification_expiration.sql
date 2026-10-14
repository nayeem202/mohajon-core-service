ALTER TABLE platform_notifications
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Existing announcements should remain in the notification history, but must
-- not unexpectedly appear as login dialogs forever after this rollout.
UPDATE platform_notifications
SET expires_at = created_at AT TIME ZONE 'UTC'
WHERE expires_at IS NULL;

ALTER TABLE platform_notifications
    ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_platform_notifications_active_expiry
    ON platform_notifications (expires_at, created_at DESC);
