import type { SVGProps } from 'react';

/**
 * Inline icon set for the weekly-commit remote.
 *
 * Authored as plain SVG components instead of a third-party icon dep on purpose:
 *
 *   - Tech-stack lock (CLAUDE.md): no new top-level frontend deps without
 *     justification, and every icon used here is a 5-15 line shape that does not
 *     warrant pulling in a library.
 *   - Bundle weight: lucide-react / @heroicons / similar would add ~10-30 KB to
 *     the federated remote. The whole set below is < 3 KB minified.
 *   - Predictability: SVGs render identically across host themes; nothing
 *     dynamic-loads at runtime, no risk of the icon set version-skewing with the
 *     host's dep tree.
 *
 * Convention: every icon is `1em` square, inherits `currentColor`, and accepts
 * the standard SVG props so call sites can apply Tailwind classes (size, colour,
 * stroke). Stroke widths sit at 1.6 to match the slightly heavier visual weight
 * the redesign uses for tier glyphs and section affordances.
 */

type IconProps = SVGProps<SVGSVGElement>;

function base(props: IconProps): IconProps {
  return {
    width: '1em',
    height: '1em',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.6,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    'aria-hidden': props['aria-label'] == null ? true : undefined,
    ...props,
  };
}

/**
 * Chess-tier glyphs. Stylised silhouettes rather than realistic chess pieces -
 * Rock is a faceted boulder, Pebble is a rounded stone, Sand is a tilted
 * mound of grains. Each one is filled (not stroke-only) so the tier colour
 * carries weight in the UI.
 */
export function RockIcon(props: IconProps) {
  return (
    <svg {...base(props)} fill="currentColor" stroke="none">
      <path d="M5.5 14.5 9 7.5h6l3.5 7-3 4.5H8.5z" />
      <path d="m9 7.5 3 4 3-4" stroke="rgb(255 255 255 / 0.45)" strokeWidth="1" />
      <path d="m12 11.5-3 7.5" stroke="rgb(255 255 255 / 0.35)" strokeWidth="1" />
      <path d="m12 11.5 3 7.5" stroke="rgb(255 255 255 / 0.35)" strokeWidth="1" />
    </svg>
  );
}

export function PebbleIcon(props: IconProps) {
  return (
    <svg {...base(props)} fill="currentColor" stroke="none">
      <ellipse cx="12" cy="13" rx="7" ry="5" />
      <ellipse cx="10" cy="11.5" rx="2" ry="1" fill="rgb(255 255 255 / 0.45)" />
    </svg>
  );
}

export function SandIcon(props: IconProps) {
  return (
    <svg {...base(props)} fill="currentColor" stroke="none">
      <circle cx="8" cy="16" r="1.4" />
      <circle cx="12" cy="14" r="1.6" />
      <circle cx="16" cy="16" r="1.4" />
      <circle cx="10.5" cy="17.5" r="1" />
      <circle cx="13.5" cy="17.5" r="1" />
    </svg>
  );
}

/**
 * State / action icons. All stroke-only, sized to the typography scale.
 */
export function CalendarIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <rect x="3.5" y="5" width="17" height="15" rx="2" />
      <path d="M3.5 9.5h17M8 3v3M16 3v3" />
    </svg>
  );
}

export function LockIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <rect x="5" y="11" width="14" height="9" rx="1.5" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  );
}

export function CheckCircleIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="12" cy="12" r="9" />
      <path d="m8.5 12 2.5 2.5L16 9.5" />
    </svg>
  );
}

export function FlagIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M5 21V4" />
      <path d="M5 4h11l-2 4 2 4H5" />
    </svg>
  );
}

export function ArrowRightIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

export function ChevronRightIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="m9 6 6 6-6 6" />
    </svg>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  );
}

export function PlusIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

export function PencilIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M4 20h4l11-11-4-4L4 16v4z" />
      <path d="m13.5 6.5 4 4" />
    </svg>
  );
}

export function TrashIcon(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" />
    </svg>
  );
}

export function SparkleIcon(props: IconProps) {
  return (
    <svg {...base(props)} fill="currentColor" stroke="none">
      <path d="m12 3 1.6 5.4L19 10l-5.4 1.6L12 17l-1.6-5.4L5 10l5.4-1.6z" />
    </svg>
  );
}
