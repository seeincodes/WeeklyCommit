import { useId } from 'react';

const MAX_CHARS = 500;
// Two-stage warning ladder per Phase-2 polish (TASK_LIST.md group 19).
// Soft warning at >=480 chars: amber, "20 left" framing -- still
// approachable. Hard warning at >=495 chars: red, "5 left", urgency.
// Browser maxLength prevents anyone from exceeding 500; the counter is
// purely advisory.
const SOFT_WARN_AT = 480;
const HARD_WARN_AT = 495;

interface ReflectionFieldProps {
  value: string;
  onChange: (next: string) => void;
}

type WarnLevel = 'none' | 'soft' | 'hard';

function warnLevelFor(used: number): WarnLevel {
  if (used >= HARD_WARN_AT) return 'hard';
  if (used >= SOFT_WARN_AT) return 'soft';
  return 'none';
}

/**
 * Reflection note input -- plain text, capped at 500 characters per [MVP5].
 * Surfaces a live "X chars left" counter that escalates from neutral to
 * amber (>=480 chars, "20 left") to red (>=495, "5 left") so the IC has
 * two distinct cues before they hit the wall.
 *
 * Native `maxLength` is the actual cap (browser enforces); the counter +
 * warning ladder are advisory. The textarea is controlled via the
 * value/onChange pair so the parent can drive it from Redux or from a
 * RTK Query mutation.
 */
export function ReflectionField({ value, onChange }: ReflectionFieldProps) {
  const id = useId();
  const used = value.length;
  const remaining = MAX_CHARS - used;
  const level = warnLevelFor(used);

  const counterClasses = {
    none: 'text-xs text-gray-500',
    soft: 'text-xs font-medium text-amber-600',
    hard: 'text-xs font-semibold text-red-600',
  }[level];

  // Amber warning at 480+ also subtly tints the textarea border to draw
  // the eye there. Red at 495+ continues the escalation. This keeps the
  // counter as the primary signal but reinforces it visually for users
  // who are deep in writing-mode.
  const textareaBorderClasses = {
    none: 'border-gray-300 focus:border-blue-500 focus:ring-blue-500',
    soft: 'border-amber-400 focus:border-amber-500 focus:ring-amber-500',
    hard: 'border-red-400 focus:border-red-500 focus:ring-red-500',
  }[level];

  // Counter copy: count down from the warning point so the IC sees the
  // remaining slack rather than a number creeping toward a ceiling.
  const counterText = level === 'none' ? `${used}/${MAX_CHARS}` : `${remaining} left`;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-gray-700">
        Reflection
      </label>
      <textarea
        id={id}
        aria-label="Reflection"
        rows={4}
        maxLength={MAX_CHARS}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`rounded border px-3 py-2 text-sm transition-colors focus:ring-1 ${textareaBorderClasses}`}
      />
      <div
        data-testid="reflection-counter"
        data-warning={level === 'soft' || level === 'hard' ? 'true' : 'false'}
        data-warn-level={level}
        aria-live="polite"
        className={counterClasses}
      >
        {counterText}
      </div>
    </div>
  );
}
