ALTER TABLE configuration
    ADD COLUMN in_app_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN browser_push_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;