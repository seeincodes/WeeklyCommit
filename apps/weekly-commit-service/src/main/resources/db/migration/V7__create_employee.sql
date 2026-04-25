-- V7: employee
--
-- Projection of authoritative employee data from Auth0. The JWT carries manager_id and org_id
-- per claim A5; this table lets us answer "who reports to whom" at query time (needed for team
-- rollups, null-manager admin reports, and unassigned-employee surfaces).
--
-- Sync strategy: a scheduled Auth0 Management API pull upserts rows on last_synced_at. Ownership
-- of the row is Auth0; this table is a projection, not a source of truth. No FK from
-- weekly_plan.employee_id because plans predate a sync, and we never want the sync lagging to
-- break plan writes.

CREATE TABLE employee (
    id                 UUID         PRIMARY KEY,
    manager_id         UUID,
    org_id             UUID         NOT NULL,
    display_name       VARCHAR(200),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    last_synced_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         VARCHAR(64)  NOT NULL,
    created_date       TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(64)  NOT NULL,
    last_modified_date TIMESTAMPTZ  NOT NULL
);

-- Team lookup (rollup, plans/team): the hot path is "all employees with manager_id = :m".
-- Partial index skips unassigned rows — they're the bulk of admin-report queries below.
CREATE INDEX idx_employee_manager
    ON employee(manager_id) WHERE manager_id IS NOT NULL;

-- Unassigned-employees admin report.
CREATE INDEX idx_employee_unassigned
    ON employee(org_id) WHERE manager_id IS NULL;

-- Full org scan (tenant isolation).
CREATE INDEX idx_employee_org ON employee(org_id);
