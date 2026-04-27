import type { ComponentType, SVGProps } from 'react';
import { PebbleIcon, RockIcon, SandIcon } from './index';

/**
 * Tier descriptor used by ChessTier and related surfaces. Lives in its own file
 * (rather than next to the icon components) so HMR / react-refresh can keep
 * the icon module purely component-exporting -- a mixed module breaks fast
 * refresh in dev. See react-refresh/only-export-components rule.
 *
 * Each entry packages the visual metadata for a single tier so call sites
 * don't have to hand-pick a colour palette per surface.
 */

export type Tier = 'ROCK' | 'PEBBLE' | 'SAND';

type IconComponent = ComponentType<SVGProps<SVGSVGElement>>;

export interface TierVisual {
  Icon: IconComponent;
  label: string;
  /** Soft surface tint -- used for tier section backgrounds. */
  surface: string;
  /** Border colour at default weight. */
  border: string;
  /** Text colour for headings + glyphs against the soft surface. */
  ink: string;
  /** Pill-style chip bg when the tier appears as a count token. */
  chipBg: string;
  /** Pill-style chip text. */
  chipText: string;
  /**
   * Accent colour the chess-spine uses on the leading rail of a tier section.
   * One step darker than `border` so the tier reads even when the section
   * background is muted.
   */
  rail: string;
}

export const TIER_META: Record<Tier, TierVisual> = {
  ROCK: {
    Icon: RockIcon,
    label: 'Rock',
    surface: 'bg-rock-bg/70',
    border: 'border-rock-border',
    ink: 'text-rock-ink',
    chipBg: 'bg-rock-bg',
    chipText: 'text-rock-ink',
    rail: 'bg-rock-ink',
  },
  PEBBLE: {
    Icon: PebbleIcon,
    label: 'Pebble',
    surface: 'bg-pebble-bg/70',
    border: 'border-pebble-border',
    ink: 'text-pebble-ink',
    chipBg: 'bg-pebble-bg',
    chipText: 'text-pebble-ink',
    rail: 'bg-pebble-ink',
  },
  SAND: {
    Icon: SandIcon,
    label: 'Sand',
    surface: 'bg-sand-bg/70',
    border: 'border-sand-border',
    ink: 'text-sand-ink',
    chipBg: 'bg-sand-bg',
    chipText: 'text-sand-ink',
    rail: 'bg-sand-ink',
  },
};

export const TIERS_ORDERED: Tier[] = ['ROCK', 'PEBBLE', 'SAND'];
