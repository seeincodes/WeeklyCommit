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
 */
export default {
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
    flowbite.content(), // pulls in flowbite-react's class names so JIT doesn't purge them
  ],
  theme: {
    extend: {
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
      },
      animation: {
        'slide-in-right': 'slideInRight 180ms ease-out both',
        'stuck-pulse': 'stuckPulse 600ms ease-in-out 1',
      },
    },
  },
  plugins: [flowbite.plugin()],
} satisfies Config;
