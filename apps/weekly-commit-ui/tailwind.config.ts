import type { Config } from 'tailwindcss';
import flowbite from 'flowbite-react/tailwind';

/**
 * Tailwind config for the weekly-commit remote.
 *
 * Design-token strategy per ADR-0004: Tailwind preset override is the primary mechanism.
 * In production-federation mode the host's design-system Tailwind preset is added to `presets`
 * via VITE_HOST_DESIGN_SYSTEM_PRESET. Locally and in standalone mode, we ship reasonable
 * Tailwind defaults so the dev surface still renders.
 *
 * If the host uses CSS variables for tokens, those bleed through whatever component classes
 * are applied -- no Tailwind-side change needed for that path.
 *
 * The semantic tokens defined under `colors` (brand, rock, pebble, sand, warn, danger, ok)
 * are *defaults* used by standalone-dev and as the visual-design baseline in tests. The host
 * preset is free to override any of them. Names are chosen to be product-specific (rock /
 * pebble / sand mirror the chess metaphor) rather than generic (primary / accent), which
 * minimises collision risk with whatever the host design system already defines.
 */
export default {
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
    flowbite.content(), // pulls in flowbite-react's class names so JIT doesn't purge them
  ],
  theme: {
    extend: {
      colors: {
        // Product-specific tier palette. The chess metaphor maps to warm-stone-cool:
        // Rock is the heaviest, warmest accent; Sand is the lightest, most neutral.
        // Each tier exposes three shades: bg (soft surface), border, and ink (text/glyph).
        rock: {
          bg: '#fef3c7', // amber-100
          border: '#fcd34d', // amber-300
          ink: '#92400e', // amber-800
        },
        pebble: {
          bg: '#e0f2fe', // sky-100
          border: '#7dd3fc', // sky-300
          ink: '#075985', // sky-800
        },
        sand: {
          bg: '#f5f5f4', // stone-100
          border: '#d6d3d1', // stone-300
          ink: '#44403c', // stone-700
        },
        // Action / status palette. Brand drives primary CTAs across the surface;
        // warn / danger / ok are reserved for state and flag colour. Using semantic
        // names instead of `bg-indigo-600` scattered through call sites lets the
        // host preset re-skin the whole product without touching component code.
        brand: {
          DEFAULT: '#4f46e5', // indigo-600
          hover: '#4338ca', // indigo-700
          soft: '#eef2ff', // indigo-50
          ink: '#312e81', // indigo-900
        },
        warn: {
          DEFAULT: '#d97706', // amber-600 (orange-toned warn)
          soft: '#fff7ed', // orange-50
          ink: '#9a3412', // orange-800
        },
        danger: {
          DEFAULT: '#e11d48', // rose-600
          soft: '#fff1f2', // rose-50
          ink: '#9f1239', // rose-800
        },
        ok: {
          DEFAULT: '#059669', // emerald-600
          soft: '#ecfdf5', // emerald-50
          ink: '#065f46', // emerald-800
        },
      },
      fontSize: {
        // A 4-step display + body scale. Hint values keep the rhythm tight without
        // requiring per-element tracking overrides at call sites. Pair with `font-display`
        // / `font-title` semantics in JSX rather than raw `text-3xl`.
        display: [
          '1.875rem',
          { lineHeight: '2.25rem', letterSpacing: '-0.02em', fontWeight: '600' },
        ],
        title: ['1.25rem', { lineHeight: '1.75rem', letterSpacing: '-0.01em', fontWeight: '600' }],
        body: ['0.9375rem', { lineHeight: '1.5rem', fontWeight: '400' }],
        meta: ['0.75rem', { lineHeight: '1rem', letterSpacing: '0.06em', fontWeight: '500' }],
      },
      boxShadow: {
        // Three soft elevation steps. The product is dense and tabular -- heavy drop
        // shadows fight the layout. Keeping these subtle is intentional.
        'soft-sm': '0 1px 2px 0 rgb(15 23 42 / 0.04), 0 1px 1px 0 rgb(15 23 42 / 0.03)',
        soft: '0 1px 3px 0 rgb(15 23 42 / 0.06), 0 2px 4px -2px rgb(15 23 42 / 0.04)',
        'soft-lg': '0 4px 12px -2px rgb(15 23 42 / 0.08), 0 6px 16px -4px rgb(15 23 42 / 0.06)',
      },
      // Animations are authored in the remote (not the host design system) because they're
      // tied to the remote's own UX moments -- the carry-streak ≥2→≥3 transition, the
      // ConflictToast slide-in. The host design system focuses on color/type/spacing tokens.
      // All are gated on `motion-safe:` at call sites so prefers-reduced-motion users
      // get the result without the motion. See [TASK_LIST.md] group 19.
      keyframes: {
        slideInRight: {
          '0%': { transform: 'translateX(120%)', opacity: '0' },
          '100%': { transform: 'translateX(0)', opacity: '1' },
        },
        stuckPulse: {
          // Played once when CarryStreakBadge flips from neutral (≥2) to "stuck" (≥3).
          '0%, 100%': { transform: 'scale(1)' },
          '50%': { transform: 'scale(1.15)' },
        },
        fadeInUp: {
          // Used by empty-state illustrations on first paint so the screen doesn't
          // pop in. Subtle by design -- 220ms ease-out, 8px translate.
          '0%': { transform: 'translateY(8px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
      },
      animation: {
        'slide-in-right': 'slideInRight 180ms ease-out both',
        'stuck-pulse': 'stuckPulse 600ms ease-in-out 1',
        'fade-in-up': 'fadeInUp 220ms ease-out both',
      },
    },
  },
  plugins: [flowbite.plugin()],
} satisfies Config;
