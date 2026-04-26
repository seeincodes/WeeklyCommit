import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import type { SupportingOutcome } from '@wc/rtk-api-client';
import { RCDOPicker } from './RCDOPicker';

const SAMPLE: SupportingOutcome[] = [
  {
    id: 'so_alignment',
    label: 'Alignment tooling GA',
    active: true,
    breadcrumb: {
      rallyCry: { id: 'rc_01', label: 'Unblock product-led growth' },
      definingObjective: { id: 'do_04', label: 'Product-led GTM' },
      coreOutcome: { id: 'co_09', label: 'Tooling readiness' },
      supportingOutcome: { id: 'so_alignment', label: 'Alignment tooling GA' },
    },
  },
  {
    id: 'so_onboarding',
    label: 'Self-serve onboarding GA',
    active: true,
    breadcrumb: {
      rallyCry: { id: 'rc_01', label: 'Unblock product-led growth' },
      definingObjective: { id: 'do_07', label: 'Activation' },
      coreOutcome: { id: 'co_12', label: 'Time to first value' },
      supportingOutcome: { id: 'so_onboarding', label: 'Self-serve onboarding GA' },
    },
  },
];

describe('<RCDOPicker />', () => {
  it('renders the typeahead input with an accessible label', () => {
    render(<RCDOPicker outcomes={SAMPLE} onSelect={vi.fn()} />);
    expect(screen.getByRole('combobox', { name: /supporting outcome/i })).toBeInTheDocument();
  });

  it('filters outcomes by typeahead query (case-insensitive)', async () => {
    const user = userEvent.setup();
    render(<RCDOPicker outcomes={SAMPLE} onSelect={vi.fn()} />);

    await user.type(screen.getByRole('combobox', { name: /supporting outcome/i }), 'onboard');

    // Assert against role=option so we count rows once (the label appears twice
    // per row -- once as the row title and once as the breadcrumb leaf).
    const options = screen.getAllByRole('option');
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent(/self-serve onboarding ga/i);
  });

  it('renders the 4-level breadcrumb for each option', () => {
    render(<RCDOPicker outcomes={SAMPLE} onSelect={vi.fn()} />);

    // The breadcrumb separator " > " between adjacent labels is the canonical
    // shape; assert the chained string for the alignment row.
    expect(
      screen.getByText(
        /Unblock product-led growth.*Product-led GTM.*Tooling readiness.*Alignment tooling GA/,
      ),
    ).toBeInTheDocument();
  });

  it('calls onSelect with the chosen outcome', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(<RCDOPicker outcomes={SAMPLE} onSelect={onSelect} />);

    await user.click(screen.getByRole('option', { name: /alignment tooling ga/i }));

    expect(onSelect).toHaveBeenCalledOnce();
    expect(onSelect).toHaveBeenCalledWith(SAMPLE[0]);
  });

  it('shows the empty state when the filter matches nothing', async () => {
    const user = userEvent.setup();
    render(<RCDOPicker outcomes={SAMPLE} onSelect={vi.fn()} />);

    await user.type(screen.getByRole('combobox', { name: /supporting outcome/i }), 'nonexistent');

    expect(screen.getByTestId('rcdo-picker-empty')).toBeInTheDocument();
  });

  it('shows the stale-cache banner when isStale is true', () => {
    render(<RCDOPicker outcomes={SAMPLE} onSelect={vi.fn()} isStale />);
    expect(screen.getByTestId('rcdo-picker-stale-banner')).toBeInTheDocument();
  });

  it('omits the stale-cache banner when isStale is false / unset', () => {
    render(<RCDOPicker outcomes={SAMPLE} onSelect={vi.fn()} />);
    expect(screen.queryByTestId('rcdo-picker-stale-banner')).not.toBeInTheDocument();
  });
});
