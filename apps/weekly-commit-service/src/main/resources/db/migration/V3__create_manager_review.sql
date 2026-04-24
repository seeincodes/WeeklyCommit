-- V3: manager_review
-- One row per manager acknowledgement. Appendable on RECONCILED plans.

CREATE TABLE manager_review (
    id                 UUID         PRIMARY KEY,
    plan_id            UUID         NOT NULL,
    manager_id         UUID         NOT NULL,
    comment            TEXT,
    acknowledged_at    TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(64)  NOT NULL,
    created_date       TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(64)  NOT NULL,
    last_modified_date TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_manager_review_plan
        FOREIGN KEY (plan_id) REFERENCES weekly_plan(id) ON DELETE CASCADE
);

CREATE INDEX idx_manager_review_plan ON manager_review(plan_id);
