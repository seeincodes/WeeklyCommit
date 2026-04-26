# Mockups

Reference HTML mockups for the Weekly Commit module. **These are design-intent artifacts**, not production code. Production lives in [apps/weekly-commit-ui/](../../apps/weekly-commit-ui/) and is built as a Vite + Module Federation remote per [TECH_STACK.md](../TECH_STACK.md).

## Files

- [weekly-planning.html](weekly-planning.html) — IC core surface (`/weekly-commit/current`). Five state variants on one page: Blank, DRAFT, LOCKED pre-day-4, LOCKED reconciliation mode, RECONCILED. Plus the global conflict-toast slot.
- [manager-rollup.html](manager-rollup.html) — Manager surfaces. Two on one page: team rollup (`/weekly-commit/team`) and IC deep-dive drawer (`/weekly-commit/team/:employeeId`).

## What changed from the original mockups

The first-pass mockups were generic "enterprise strategy dashboard" designs with a fictional STRATOS brand. They didn't match v1 scope. Reconciliation against [PRD.md](../PRD.md) and [MEMO.md](../MEMO.md) drove these changes:

### Cut entirely (not in v1 scope)

| Original affordance | Why cut |
|---|---|
| Per-commit comment threads (chat-bubble + count) | PRD §"Out of scope": *per-commit comments or approval gating*. v1 has one plan-level comment ([MVP10]) only. |
| Multi-collaborator avatar stack on commits (`+3 collaborators`) | No multi-assignee model in v1. Each commit has one owner via the parent plan. |
| Data Insights / Team Radar / "Schedule Sync" cards | Analytics deferred ([PRD.md](../PRD.md) "Out of scope"); Outlook integration deferred to v2 (§12 Q15). |
| Strategic Alignment 88.4 score with `+2.4% vs Last Month` deltas | No historical trend in v1 — would require analytics materialized view that's deferred. Render alignment % flat. |
| "Resource Distribution" R/C/D bar chart | Wrong taxonomy. The original used Run/Change/Disrupt portfolio framing. RCDO is **R**ally Cry → **D**efining Objective → **C**ore Outcome → **S**upporting Outcome — a 4-level *tree*, not a 3-bucket portfolio. |
| "RCDO Hierarchy" RUN/CHANGE/DISRUPT panel | Same wrong taxonomy. Replaced with a **By Supporting Outcome** aggregation showing real linked-commit counts. |
| "Critical Risks" panel as a separate dashboard tile | Concept absorbed into the rollup itself as the "Needs you" stat card + flagged-first member ordering. |
| STRATOS branding | The remote runs inside the Performance Assessment host. The host owns brand. Mockups show neutral "Performance Assessment / Weekly Commit" framing. |

### Reworked

| Original | Revised |
|---|---|
| Colored badges "Critical Path" / "Operational" on commits | **Chess tier control** (Rock / Pebble / Sand) — required field per [MVP2]. Top Rock surfaces an extra `★ Top Rock` indicator (derived, not stored — [MEMO.md](../MEMO.md) decision #7). |
| Vertical 3-node "Strategic Path" (Company Goal → Team Outcome → My Commitment) | **4-node RCDO breadcrumb** — Rally Cry → Defining Objective → Core Outcome → Supporting Outcome. Driven by the currently selected commit's linked Supporting Outcome. The first three are read-only ancestry; only the Supporting Outcome is the actual link target ([MVP3], MEMO decision #8 — 1:1 commit→Supporting Outcome). |
| "Manager Strategy Dashboard" multi-tile concept | **Team rollup** ([MVP9]) — flagged-first member cards with Top Rock, tier shape (Rock/Pebble/Sand counts), reflection preview ~80 chars, and flag chips. The IC deep-dive is a **drawer overlay**, not a separate page ([MVP10]). |

### Added (required for v1 — were missing from originals)

- **State badge + next-action hint** ([MVP4], [MVP5]) — DRAFT / LOCKED / RECONCILED with deadline copy (e.g. "Lock by Fri May 1, 5pm PT").
- **Reflection note field** ([MVP5]) — ≤500 chars, plain text, char counter, only editable in DRAFT or reconciliation mode.
- **Reconciliation mode UI** ([MVP5]) — DONE / PARTIAL / MISSED tri-state per commit + `actualNote` textarea per row. Available when `state=LOCKED AND now ≥ weekStart + 4d`.
- **Carry-forward controls** ([MVP6]) — per-row "Carry forward" + plan-level "Carry all missed/partial" button. Visible only in reconciliation mode.
- **Carry-streak badge / stuck flag** ([MVP7]) — small "Carried 2×" chip at streak ≥ 2; escalated to red "Stuck" styling at ≥ 3. 52-hop cap enforced server-side ([MEMO.md](../MEMO.md) decision #4 cap).
- **Top Rock indicator** ([MVP8]) — `★ Top Rock` callout on the derived first Rock by `displayOrder`. "No Top Rock" itself is a manager-facing flag.
- **Blank state** ([MVP1], MEMO decision #10) — explicit "Create plan" CTA. No auto-create on route entry.
- **Conflict toast** ([MVP13]) — global slot for HTTP 409 from the optimistic `@Version` check on `WeeklyPlan`.
- **Audit log link** ([MVP17]) — surfaced from the IC drawer.

## v1 scope traceability

Every visual element above is traceable to a numbered MVP item in [PRD.md](../PRD.md). If the implementation diverges, update the mockup or the PRD — don't let them drift.

| MVP | Mockup element |
|---|---|
| [MVP1] Create current-week plan | weekly-planning State 1 (Blank) "Create plan" CTA |
| [MVP2] CRUD WeeklyCommit in DRAFT | weekly-planning State 2 commit list, chess tier control, reorder, tag chips |
| [MVP3] RCDO consumed read-only | weekly-planning Strategic Path breadcrumb (Rally Cry → DO → CO → SO) |
| [MVP4] Lock week (manual + auto) | weekly-planning State 2 "Lock this week" CTA + State 3 LOCKED read-only |
| [MVP5] Reconciliation mode | weekly-planning State 4 — DONE/PARTIAL/MISSED + actualNote + reflection |
| [MVP6] Carry-forward (per-item + carry-all) | weekly-planning State 4 — per-row + plan-level controls |
| [MVP7] Carry-streak badge / stuck flag | weekly-planning State 2/4; manager-rollup card flag chips; drawer carry chain visualization |
| [MVP8] Top Rock derived | weekly-planning Top Rock callout; manager-rollup card Top Rock callout; drawer Top Rock callout |
| [MVP9] Team rollup | manager-rollup Surface 1 — aggregates + flagged-first member cards + byOutcome |
| [MVP10] IC deep-dive drawer | manager-rollup Surface 2 — overlay with full plan, full reflection, streak chains, plan-level comment, ack action |
| [MVP11] Unreviewed-72h digest | manager-rollup "Unreviewed > 72h" stat + per-card "Unreviewed Nh" flag |
| [MVP13] Optimistic-lock 409 → conflict toast | weekly-planning conflict toast slot |
| [MVP17] Audit log access | manager-rollup drawer "View audit log" link |
| [MVP21] Week boundaries UTC at service / employee TZ at UI | weekly-planning lock deadline copy "Fri May 1, 5pm PT" (TZ-rendered) |
| [MVP22] Null-manager handling | manager-rollup "No plan this week" card pattern |
| [MVP24] Kill-switch fallback | not depicted in mockups — lives in host shell, not the remote |

Items not yet depicted: [MVP12] (state machine — backend only, no UI), [MVP14]–[MVP16] (notification/scheduled jobs — backend only), [MVP18]–[MVP20] (build/deploy/auth — infra), [MVP23] (Flyway — backend).

## Design system notes

These tokens carry forward into the Tailwind config in [apps/weekly-commit-ui/](../../apps/weekly-commit-ui/) when the frontend scaffold lands (task group 9 in [TASK_LIST.md](../TASK_LIST.md)):

- **Color palette** — Material 3 expressive dark theme. Background `#081425`, primary-container `#0066ff`, surface tones step lighter via `surface-container-low/-/-high/-highest`.
- **Typography** — Inter at six named scales: `headline-xl/lg/md`, `body-lg/md`, `label-md`, plus `mono-data` for IDs and timestamps.
- **Spacing** — `gutter` 24px, `margin` 32px, `density-comfortable` 16px, `density-compact` 8px. `container-max` 1440px.
- **Glass-card surface** — `rgba(15,23,42,0.6)` with `backdrop-filter: blur(24px)` and an inset 1px highlight. Distinctive enough to keep; check against host design system before locking.
- **Tier color coding** — Rock = primary-container blue, Pebble = tertiary muted blue, Sand = neutral slate. Top Rock gets an amber accent (the only non-blue accent in the palette to make it visually distinct).
- **Flag chip colors** — Stuck = error-red, Unreviewed-72h = amber, No-Top-Rock = slate, Missing-reflection = tertiary-blue, All-clear = emerald.

**Caveat:** the specific palette is a placeholder until the [host design system token override spike](../adr/0004-flowbite-token-override.md) confirms what the PA host actually uses. Mockup colors are *aesthetic intent* — the production palette inherits from the host.

## What's still TBD

- The byOutcome aggregation panel renders with placeholder data; full RCDO ancestry hydration arrives with the [RcdoClient in task group 7](../TASK_LIST.md#7-backend-integrations-rcdo--notification-svc).
- Empty-state illustrations for the Blank state are deferred to Phase 2 polish ([TASK_LIST.md](../TASK_LIST.md) group 19).
- Accessibility pass (WCAG 2.1 AA) is Phase 2 (group 16).
- Responsive breakpoints below `md` are not depicted in detail; production must handle narrow viewports.
