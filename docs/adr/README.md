# Architecture Decision Records

Numbered, immutable records of significant decisions. New decisions that
supersede earlier ones get a new ADR; never edit the decision section of a
prior ADR — mark it `Superseded by ADR-NNNN` in Status.

## Log

| # | Status | Title |
|---|---|---|
| [0001](0001-rcdo-contract.md) | Proposed (stubbed) | RCDO read API contract |
| [0002](0002-notification-svc-contract.md) | Proposed (stubbed) | notification-svc send contract |
| [0003](0003-pm-remote-vite-config-mirror.md) | Proposed (stubbed) | PM remote `vite.config.ts` shape mirror |
| [0004](0004-flowbite-token-override.md) | Proposed (stubbed) | Flowbite vs. `@host/design-system` token strategy |

## Status values

- **Proposed (stubbed)** — decision captured but inputs are invented. Must be
  validated against the real upstream before any downstream code depends on it.
- **Proposed** — decision captured; real inputs gathered; awaiting implementation.
- **Accepted** — implemented and in use.
- **Deprecated** — no longer current; supersession optional.
- **Superseded by ADR-NNNN** — see the named record.

## Template

Use [_template.md](_template.md) when adding a new ADR. Number sequentially,
kebab-case the filename: `NNNN-short-slug.md`. Update the log table above.
