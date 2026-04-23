# ADR-0004 — Flowbite vs. `@host/design-system` token strategy

- **Status:** Proposed (stubbed)
- **Date:** 2026-04-23
- **Deciders:** Xian (solo driver); host design-system maintainer TBD
- **Relates to:** Presearch §6 Frontend Styling, PRD performance/UX, MEMO "typography does the hierarchy work" principle, TASK_LIST group 9, [CLAUDE.md](../../CLAUDE.md) tech stack lock

## Context

The brief names Flowbite React + Tailwind as required. The PA host ships
a `@host/design-system` (assumed) whose tokens should own color, radius,
spacing, elevation, and dark-mode semantics inside the weekly-commit
remote. If Flowbite's defaults don't yield to host tokens, the remote looks
foreign inside the host — which undercuts "the interface is discreet"
(presearch §6 design principles).

We need a definitive answer to: *can host tokens override Flowbite
defaults cleanly?* If yes, we stay on Flowbite. If not, we fall back to
Headless UI + Tailwind (pre-approved in the tech stack lock).

**Inputs for this ADR are invented.** No real override was attempted
against the host design system. Findings at
[../spikes/flowbite-token-override-findings.md](../spikes/flowbite-token-override-findings.md).
Status is **Proposed (stubbed)** until validated.

## Decision

Stubbed path forward:

1. **Primary path — Flowbite + host Tailwind preset + Flowbite theme prop.**
   `tailwind.config.ts` extends `@host/design-system/tailwind-preset`.
   `<Flowbite theme={{ theme: wcTheme }}>` wraps the module root, where
   `wcTheme` composes the host's Flowbite theme helper (if one exists) or
   a hand-built theme object.
2. **Fallback — Headless UI + Tailwind** if the real override spike shows
   that ≥3 v1 components cannot be styled to match host visual language via
   the Flowbite path.
3. **Decision gate** — the spike is run at the start of group 9 with a
   concrete checklist (below). Switching frameworks mid-group 9 is
   acceptable; switching after group 11 (IC surfaces) is not, because the
   component API diverges meaningfully.

## Alternatives considered

1. **Accept Flowbite's defaults.** Rejected. The remote would not feel
   native to the host and reviewers would (rightly) reject the visual
   integration.
2. **Custom CSS from scratch, no component library.** Rejected. Brief
   requires Flowbite. Also high-cost for the v1 timeline.
3. **shadcn/ui or Radix + Tailwind.** Rejected. Out of the locked tech
   stack; would require brief-deviation ratification. Headless UI is
   already the approved fallback and covers the same design space.
4. **Inline every Tailwind token per component.** Rejected. Defeats the
   purpose of a design system.

## Consequences

- **Positive**: one theming mechanism for the whole remote. Components from
  `libs/ui-components` wrap Flowbite once and consumers don't think about
  theming again. If the fallback triggers, the swap is a one-library
  change localized to `libs/ui-components` — consumers keep the same API.
- **Negative**: Flowbite-React's theme-prop API is class-string-based;
  some visually subtle tokens (focus-ring treatments, dense typography
  scales, elevation tiers) may still leak Flowbite defaults. We budget a
  polish pass in group 19 for this.
- **Follow-ups**:
  - Real spike run at start of group 9 (checklist below).
  - `libs/ui-components` exports wrapper components that consumers import
    instead of raw Flowbite — makes the fallback swap mechanical.
  - Dark mode mechanism (class vs. attribute) confirmed before any
    dark-mode styling is applied.

## Validation

Upgrades to **Accepted** (Flowbite path) when:

1. `tailwind.config.ts` extends the real `@host/design-system/tailwind-preset`
   and five canary components render with host-native tokens:
   - `<Button>` primary + secondary
   - `<TextInput>` and typeahead dropdown
   - `<Modal>` or drawer (for `<IcDrawer />` fit)
   - Table header + row (for `<ReconcileTable />` fit)
   - Toast / alert (for `<ConflictToast />`)
2. Dark-mode toggle (host-driven) flips the remote's palette correctly.
3. No Flowbite-default color, radius, or spacing leaks into a screenshot
   diff against the host's existing PM remote.

Downgrades to **fallback** (Headless UI) if any of the five canaries
requires more than 30 minutes of override wrangling or produces visual
regressions that wouldn't pass host design review. In that case: write
ADR-0005 superseding this one, document the Headless UI + Tailwind
component mapping, and keep the `libs/ui-components` wrapper API stable.
