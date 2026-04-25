-- V8: shedlock
--
-- Distributed lock table for Shedlock (https://github.com/lukas-krecan/ShedLock).
-- Coordinates scheduled-job execution across multiple pods so an hourly cron with N replicas
-- still fires exactly once per cron tick. Required by AutoLockJob, ArchivalJob, and
-- UnreviewedDigestJob (group 8 / [MVP4][MVP11][MVP16]).
--
-- The Shedlock JDBC provider owns this schema; column names + types are dictated by it. Using
-- TIMESTAMPTZ (vs Shedlock's default TIMESTAMP) for parity with the rest of the schema -- all
-- other tables store time as TIMESTAMPTZ. The provider's reads/writes via JdbcTemplate work
-- against either type since both compare correctly on the JDBC side.

CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMPTZ  NOT NULL,
    locked_at   TIMESTAMPTZ  NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
