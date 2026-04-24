-- V5: audit_log
-- State transitions and manager-review events. 2-year retention gated on
-- legal sign-off (docs/PRD.md). No FK on entity_id to keep the table append-
-- only and resilient to downstream deletes; entity_type + entity_id form
-- the logical key.

CREATE TABLE audit_log (
    id             UUID         PRIMARY KEY,
    entity_type    VARCHAR(32)  NOT NULL,
    entity_id      UUID         NOT NULL,
    event_type     VARCHAR(32)  NOT NULL,
    actor_id       UUID,
    from_state     VARCHAR(16),
    to_state       VARCHAR(16),
    metadata       JSONB,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_log_entity_type
        CHECK (entity_type IN ('WEEKLY_PLAN','MANAGER_REVIEW')),
    CONSTRAINT ck_audit_log_event_type
        CHECK (event_type IN ('STATE_TRANSITION','MANAGER_REVIEW'))
);

CREATE INDEX idx_audit_log_entity
    ON audit_log(entity_type, entity_id, occurred_at DESC);
