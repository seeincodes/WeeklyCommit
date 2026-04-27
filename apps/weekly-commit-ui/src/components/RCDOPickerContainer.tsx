import { useGetSupportingOutcomesQuery } from '@wc/rtk-api-client';
import type { SupportingOutcome } from '@wc/rtk-api-client';
import { RCDOPicker } from './RCDOPicker';

interface RCDOPickerContainerProps {
  onSelect: (outcome: SupportingOutcome) => void;
}

/**
 * Data-fetching wrapper for the presentational <RCDOPicker />. Issues the
 * RTK Query for the active supporting outcomes and feeds the result into the
 * picker. Handles three render branches:
 *
 *   - loading                 : spinner placeholder, picker not yet mounted
 *   - error (any status)      : picker with `isStale=true`; falls back to the
 *                               last-known data RTK Query carries on the
 *                               `data` field even during a failed refetch,
 *                               so the IC can still pick from the cache.
 *                               First-load failure shows an empty picker
 *                               with the banner -- a degraded but non-broken
 *                               experience per MEMO Known Failure Modes.
 *   - success                 : picker populated, no banner.
 */
export function RCDOPickerContainer({ onSelect }: RCDOPickerContainerProps) {
  const { data, error, isLoading } = useGetSupportingOutcomesQuery();

  if (isLoading) {
    return (
      <div data-testid="rcdo-picker-loading" className="text-sm text-gray-500">
        Loading outcomes…
      </div>
    );
  }

  return <RCDOPicker outcomes={data ?? []} onSelect={onSelect} isStale={Boolean(error)} />;
}
