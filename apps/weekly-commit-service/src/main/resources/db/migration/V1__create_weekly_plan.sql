-- V1: weekly_plan
-- Owned by an IC (employee_id). One plan per (employee_id, week_start).
-- See docs/TECH_STACK.md#database-schema and docs/MEMO.md decisions #4, #5.

CREATE TABLE weekly_plan (
    id                     UUID PRIMARY KEY,
    employee_id            UUID         NOT NULL,
    week_start             DATE         NOT NULL,
    state                  VARCHAR(16)  NOT NULL,
    locked_at              TIMESTAMPTZ,
    reconciled_at          TIMESTAMPTZ,
    manager_reviewed_at    TIMESTAMPTZ,
    reflection_note        VARCHAR(500),
    version                BIGINT       NOT NULL DEFAULT 0,
    created_by             VARCHAR(64)  NOT NULL,
    created_date           TIMESTAMPTZ  NOT NULL,
    last_modified_by       VARCHAR(64)  NOT NULL,
    last_modified_date     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_weekly_plan_employee_week UNIQUE (employee_id, week_start),
    CONSTRAINT ck_weekly_plan_state CHECK (state IN ('DRAFT','LOCKED','RECONCILED','ARCHIVED'))
);

CREATE INDEX idx_weekly_plan_employee ON weekly_plan(employee_id);
CREATE INDEX idx_weekly_plan_state    ON weekly_plan(state);
