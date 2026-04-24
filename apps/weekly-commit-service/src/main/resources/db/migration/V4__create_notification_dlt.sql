-- V4: notification_dlt
-- Dead-letter table for failed notification sends (MEMO decision #2).
-- CloudWatch alarm fires on any row < 1h old; admin replay endpoint deletes
-- the row on successful re-send.

CREATE TABLE notification_dlt (
    id          UUID         PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    payload     JSONB        NOT NULL,
    last_error  TEXT         NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_dlt_created ON notification_dlt(created_at);
