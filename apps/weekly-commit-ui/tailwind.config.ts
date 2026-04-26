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
      // Reserved for weekly-commit-specific tokens that don't come from the host design
      // system. Keep this empty until a real need surfaces -- one-off tokens accumulate as
      // tech debt. See ADR-0004.
    },
  },
  plugins: [flowbite.plugin()],
} satisfies Config;
