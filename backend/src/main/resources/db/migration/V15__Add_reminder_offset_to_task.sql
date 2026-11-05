ALTER TABLE task
    ADD COLUMN reminder_minutes_before INTEGER,
    ADD CONSTRAINT chk_task_reminder_minutes_before
        CHECK (reminder_minutes_before BETWEEN 0 AND 525600);