/**
 * Perf-only scaffolding. Profile-gated (only loaded under {@code SPRING_PROFILES_ACTIVE=perf}) and
 * never active in production. Used by the k6 perf harness in {@code apps/weekly-commit-ui/perf/} to
 * seed enough rows that p95 measurements on the plan-retrieval endpoints are meaningful.
 */
package com.acme.weeklycommit.perf;
