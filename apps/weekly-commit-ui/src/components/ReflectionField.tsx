import { useId } from 'react';

const MAX_CHARS = 500;
// Warning threshold matches Phase-2 polish "480+ char warning color" task.
// Surfacing it now so the styling hook is ready when the polish subtask lands.
const WARN_AT = 480;

interface ReflectionFieldProps {
  value: string;
  onChange: (next: string) => void;
}

/**
 * Reflection note input -- plain text, capped at 500 characters per [MVP5].
 * Surfaces a live "<used>/500" counter; flips to a warning style at >= 480
 * chars so the IC sees the wall coming before they hit it.
 *
 * Native `maxLength` is the cap (browser enforces); the counter + warning
 * are advisory. The textarea is uncontrolled-friendly via the controlled
 * value/onChange pair so the parent can drive it from Redux or from a
 * RTK Query mutation.
 */
export function ReflectionField({ value, onChange }: ReflectionFieldProps) {
  const id = useId();
  const used = value.length;
  const isWarning = used >= WARN_AT;

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
        className="rounded border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
      />
      <div
        data-testid="reflection-counter"
        data-warning={isWarning ? 'true' : 'false'}
        className={isWarning ? 'text-xs text-orange-600' : 'text-xs text-gray-500'}
      >
        {used}/{MAX_CHARS}
      </div>
    </div>
  );
}
