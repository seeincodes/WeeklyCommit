#!/usr/bin/env bash
#
# lint-now-thresholds.sh
# ----------------------
# Enforces the week-math rule from docs/MEMO.md (decision 6, "TZ / DST edges"):
#
#   "All threshold comparisons use application-computed Instant. Never NOW() in
#    SQL — that makes TZ/DST bugs silent."
#
# CLAUDE.md tech-stack lock restates this as: week math is always UTC at the
# service layer; never compare against NOW() in SQL — always an
# application-computed Instant. Pod clock skew + NOW() in SQL = inconsistent
# thresholds across replicas under HPA.
#
# What this lint flags
# --------------------
# Any SQL or Java reference to NOW() / CURRENT_TIMESTAMP that is being used as
# a *threshold comparison* — typically in a WHERE clause, an @Query annotation,
# or repository SQL. Examples that would fail:
#
#   WHERE foo < NOW()
#   WHERE created_at >= CURRENT_TIMESTAMP
#   @Query("SELECT ... WHERE ts < NOW()")
#
# What this lint allows (allowlist)
# ---------------------------------
# 1. SQL column DEFAULTs — `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` is
#    insert-time, not a threshold, and is the standard pattern.
# 2. SQL line comments (`-- ...`) that mention NOW() for context.
# 3. Java line comments (`// ...`) and javadoc/block comments (` * ...`,
#    `/* ... */`) that mention NOW() for context — including the rule itself.
# 4. Java time-API calls: `Instant.now(...)`, `LocalDate.now(...)`,
#    `Clock.now(...)`, etc. The pattern `.now(` is the Java API, not SQL.
# 5. Java map-key string literals: `"now"` (e.g. error envelope timestamp keys).
#
# How to run locally
# ------------------
#   bash scripts/lint-now-thresholds.sh
#
# CI integration
# --------------
# This script is also exercised by NowThresholdLintTest in the
# weekly-commit-service module, so `./mvnw test` runs it automatically. CI does
# not need a separate workflow step — but if a future workflow wants to run
# the lint in isolation (e.g. before kicking off the full test suite for fail-
# fast), it can shell out to this script directly. Exit 0 = clean, exit 1 =
# violation found (with file:line:content printed to stderr).

set -euo pipefail

# Resolve repo root (the directory containing this script's parent).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"

SQL_GLOB="apps/weekly-commit-service/src/main/resources/db/migration"
JAVA_ROOT="apps/weekly-commit-service/src/main/java"

# Step 1: Collect all candidate matches (file:line:content) with case-
# insensitive word-boundary search for NOW or CURRENT_TIMESTAMP. We use
# `|| true` so an empty match set doesn't trip `set -e`.
candidates="$(
  {
    if [ -d "${SQL_GLOB}" ]; then
      grep -RInE '\b(NOW|CURRENT_TIMESTAMP)\b' "${SQL_GLOB}" || true
    fi
    if [ -d "${JAVA_ROOT}" ]; then
      grep -RInE '\b(NOW|CURRENT_TIMESTAMP)\b' "${JAVA_ROOT}" || true
    fi
  }
)"

# Step 2: Apply allowlist filters one at a time. Each `grep -v` strips lines
# that match a known-legitimate pattern. Order doesn't matter functionally,
# but readability matters — keep filters small and named.

# Allow: SQL column DEFAULTs (e.g. `DEFAULT now()`).
filtered="$(printf '%s\n' "${candidates}" | grep -ivE 'DEFAULT[[:space:]]+now[[:space:]]*\(' || true)"

# Allow: SQL line comments. After the `file:line:` prefix, the line's leading
# non-whitespace content starts with `--`.
filtered="$(printf '%s\n' "${filtered}" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*--' || true)"

# Allow: Java line comments — `// ...`.
filtered="$(printf '%s\n' "${filtered}" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*//' || true)"

# Allow: Java block / javadoc comment lines — ` * ...`, `/* ...`, lines that
# end a block with `*/`. We require the comment marker to be the first non-
# whitespace token so we don't accidentally allow code that happens to contain
# ` * ` (e.g. multiplication).
filtered="$(printf '%s\n' "${filtered}" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*\*' || true)"
filtered="$(printf '%s\n' "${filtered}" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*/\*' || true)"

# Allow: Java time-API calls — `.now(` is always the JDK API, never SQL.
# Examples: Instant.now(clock), LocalDate.now(), Clock.systemUTC().instant().
# Note: we allow the LINE (not just the call) because in practice the only
# thing that triggers `\bNOW\b` on such a line is the `.now(` itself.
filtered="$(printf '%s\n' "${filtered}" | grep -vE '\.now[[:space:]]*\(' || true)"

# Allow: Java map-key string literal `"now"` — used as an envelope/meta key in
# error responses (`Map.of("now", Instant.now().toString())`). The quoted form
# is not SQL.
filtered="$(printf '%s\n' "${filtered}" | grep -vE '"now"' || true)"

# Step 3: Drop empty lines that may have leaked through after filtering.
violations="$(printf '%s\n' "${filtered}" | grep -vE '^[[:space:]]*$' || true)"

if [ -n "${violations}" ]; then
  echo "lint-now-thresholds: forbidden NOW() / CURRENT_TIMESTAMP threshold use found." >&2
  echo "See docs/MEMO.md (week-math rule). Use an application-computed Instant instead." >&2
  echo "" >&2
  printf '%s\n' "${violations}" >&2
  exit 1
fi

echo "lint-now-thresholds: OK (no forbidden NOW()/CURRENT_TIMESTAMP threshold uses)."
exit 0
