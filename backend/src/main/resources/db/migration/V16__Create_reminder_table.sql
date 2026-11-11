CREATE TABLE reminder
(
    id                   VARCHAR(21) PRIMARY KEY,
    task_id              VARCHAR(21) NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    user_id              VARCHAR(21) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    occurrence_starts_at TIMESTAMPTZ NOT NULL,
    scheduled_at         TIMESTAMPTZ NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    claim_token          VARCHAR(36),
    lease_expires_at     TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_reminder_task_occurrence
        UNIQUE (task_id, occurrence_starts_at),

    CONSTRAINT chk_reminder_status
        CHECK (status IN (
                          'PENDING',
                          'PROCESSING',
                          'READY',
                          'SKIPPED',
                          'CANCELED'
            )),

    CONSTRAINT chk_reminder_claim
        CHECK (
            (
                status = 'PROCESSING'
                    AND claim_token IS NOT NULL
                    AND lease_expires_at IS NOT NULL
                )
                OR
            (
                status <> 'PROCESSING'
                    AND claim_token IS NULL
                    AND lease_expires_at IS NULL
                )
            )
);

CREATE INDEX idx_reminder_status_scheduled_at
    ON reminder (status, scheduled_at);

CREATE INDEX idx_reminder_status_lease_expires_at
    ON reminder (status, lease_expires_at);