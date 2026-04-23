# Flowbite × `@host/design-system` Override — Spike Findings (STUB)

**Status:** STUB. No real `@host/design-system` package was tested. These
findings are what we *expect* based on Flowbite React's theming model and
typical design-system token exports. Must be validated against the real
host design system before group 9 commits to Flowbite. See [ADR-0004](../adr/0004-flowbite-token-override.md).

## The override question

Flowbite React components carry default Tailwind classes for colors,
radius, shadow, spacing. The host's design system presumably exports tokens
(CSS custom properties, Tailwind preset, or a theme object). For the
weekly-commit remote to feel like part of the PA host — not a foreign
visual surface — Flowbite's defaults need to yield to host tokens.

Three mechanisms Flowbite supports:

1. **`Flowbite` provider `theme` prop** — pass a partial theme, Flowbite
   deep-merges with its defaults. Clean API but requires the override theme
   to be expressible in Flowbite's class-based theme shape.
2. **Tailwind preset override** — the host ships a Tailwind preset that
   redefines `colors`, `spacing`, `borderRadius`. Our `tailwind.config.ts`
   extends the host preset; Flowbite's component classes recompute against
   the new tokens. Most robust.
3. **CSS custom property layer** — host ships `:root { --wc-color-primary: ...; }`
   and our CSS uses `var(--wc-color-primary)`. Flowbite's compiled classes
   don't read CSS vars, so this only works if we also wire Flowbite's theme
   prop to the same vars.

## Stub findings

Assuming `@host/design-system` ships a Tailwind preset AND a Flowbite theme
helper:

```ts
// tailwind.config.ts (group 9)
import hostPreset from '@host/design-system/tailwind-preset';
import flowbite from 'flowbite-react/tailwind';

export default {
  presets: [hostPreset],
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
    flowbite.content(),
  ],
  plugins: [flowbite.plugin()],
};
```

```ts
// src/theme.ts (group 9)
import { createTheme } from 'flowbite-react';
import { hostFlowbiteTheme } from '@host/design-system/flowbite';

export const wcTheme = createTheme(hostFlowbiteTheme);
```

```tsx
// src/WeeklyCommitModule.tsx (group 9)
import { Flowbite } from 'flowbite-react';
import { wcTheme } from './theme';

export default function WeeklyCommitModule() {
  return <Flowbite theme={{ theme: wcTheme }}>{/* ... */}</Flowbite>;
}
```

**Stubbed conclusion:** Flowbite + host preset + host Flowbite theme covers
80% of the tokens; the remaining 20% (dense typography scales, specific
focus-ring treatments) require selective component-level theme overrides
in `src/theme.ts`.

## Fallback — if Flowbite tokens don't override cleanly

Per presearch §6 and [CLAUDE.md](../../CLAUDE.md) tech stack lock, the approved
fallback is **Headless UI + Tailwind**:

- `@headlessui/react` for interactive primitives (listbox, dialog, menu,
  popover, tabs) — all unstyled, so host tokens apply via Tailwind utility
  classes.
- `@radix-ui/react-*` NOT approved as a fallback (out of lock scope).
- All Flowbite-React imports in `libs/ui-components` become Headless UI
  equivalents; the wrapper names stay the same so consumers don't change.

Decision criterion for the real spike: if ≥3 components from the v1 set
(RCDOPicker typeahead, MemberCard, ReconcileTable row, StateBadge,
ConflictToast) can't be styled to look like host-native components using
Flowbite's theme prop + host preset, switch to Headless UI.

## Unknown at stub time

- The actual export name of the host preset — `@host/design-system/tailwind-preset`
  is a guess.
- Whether the host already provides a Flowbite-compatible theme helper. If
  not, we author one and contribute it back.
- Dark mode handling — Flowbite class-toggles `dark:`; host may drive theme
  via `data-theme` attribute. Needs validation.
