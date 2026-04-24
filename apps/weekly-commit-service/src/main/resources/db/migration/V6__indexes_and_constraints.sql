-- V6: performance-critical indexes + Shedlock table

-- Top Rock lookup (first ROCK ordered by display_order, per plan).
-- Composite covers DerivedFieldService query shape.
CREATE INDEX idx_weekly_commit_toprock
    ON weekly_commit(plan_id, chess_tier, display_order);

-- Carry-streak walk: find the source of a carried-forward commit, or the
-- next link in the chain.
CREATE INDEX idx_weekly_commit_carry
    ON weekly_commit(carried_forward_from_id);

-- Supporting-outcome analytics (rollup by outcome, audits).
CREATE INDEX idx_weekly_commit_outcome
    ON weekly_commit(supporting_outcome_id);

-- Shedlock coordination table.
-- Standard schema from net.javacrumbs.shedlock:shedlock-provider-jdbc-template
-- reference docs. Columns named to match the provider's defaults.
CREATE TABLE shedlock (
    name        VARCHAR(64)  PRIMARY KEY,
    lock_until  TIMESTAMPTZ  NOT NULL,
    locked_at   TIMESTAMPTZ  NOT NULL,
    locked_by   VARCHAR(255) NOT NULL
);
