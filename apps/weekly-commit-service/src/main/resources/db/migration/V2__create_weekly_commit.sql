-- V2: weekly_commit
-- Child of weekly_plan. Links 1:1 to a Supporting Outcome (RCDO). Self-references
-- for carry-forward chains. Non-FK indexes deferred to V6.

CREATE TABLE weekly_commit (
    id                       UUID         PRIMARY KEY,
    plan_id                  UUID         NOT NULL,
    title                    VARCHAR(200) NOT NULL,
    description              TEXT,
    supporting_outcome_id    UUID         NOT NULL,
    chess_tier               VARCHAR(8)   NOT NULL,
    category_tags            TEXT[]       NOT NULL DEFAULT '{}',
    estimated_hours          NUMERIC(4,1),
    display_order            INT          NOT NULL,
    related_meeting          VARCHAR(200),
    carried_forward_from_id  UUID,
    carried_forward_to_id    UUID,
    actual_status            VARCHAR(8)   NOT NULL DEFAULT 'PENDING',
    actual_note              TEXT,
    created_by               VARCHAR(64)  NOT NULL,
    created_date             TIMESTAMPTZ  NOT NULL,
    last_modified_by         VARCHAR(64)  NOT NULL,
    last_modified_date       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_weekly_commit_plan
        FOREIGN KEY (plan_id) REFERENCES weekly_plan(id) ON DELETE CASCADE,
    CONSTRAINT fk_weekly_commit_carried_from
        FOREIGN KEY (carried_forward_from_id) REFERENCES weekly_commit(id) ON DELETE SET NULL,
    CONSTRAINT fk_weekly_commit_carried_to
        FOREIGN KEY (carried_forward_to_id) REFERENCES weekly_commit(id) ON DELETE SET NULL,
    CONSTRAINT ck_weekly_commit_tier   CHECK (chess_tier IN ('ROCK','PEBBLE','SAND')),
    CONSTRAINT ck_weekly_commit_status CHECK (actual_status IN ('PENDING','DONE','PARTIAL','MISSED'))
);

CREATE INDEX idx_weekly_commit_plan ON weekly_commit(plan_id);
