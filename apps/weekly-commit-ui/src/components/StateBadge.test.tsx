import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StateBadge } from './StateBadge';

describe('<StateBadge />', () => {
  it('renders a DRAFT badge with the lock-week hint', () => {
    render(<StateBadge state="DRAFT" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveTextContent(/draft/i);
    expect(screen.getByTestId('state-badge-hint')).toHaveTextContent(
      /lock the week when you’re done planning/i,
    );
  });

  it('renders a LOCKED badge with the "reconciliation opens Friday" hint when not yet eligible', () => {
    render(<StateBadge state="LOCKED" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveTextContent(/locked/i);
    expect(screen.getByTestId('state-badge-hint')).toHaveTextContent(
      /reconciliation opens friday/i,
    );
  });

  it('renders a LOCKED badge with the "submit reconciliation" hint when reconcile-eligible', () => {
    render(<StateBadge state="LOCKED" isReconcileEligible />);
    expect(screen.getByTestId('state-badge')).toHaveTextContent(/locked/i);
    expect(screen.getByTestId('state-badge-hint')).toHaveTextContent(/submit reconciliation/i);
  });

  it('renders a RECONCILED badge with the carry-forward hint', () => {
    render(<StateBadge state="RECONCILED" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveTextContent(/reconciled/i);
    expect(screen.getByTestId('state-badge-hint')).toHaveTextContent(/carry forward/i);
  });

  it('renders an ARCHIVED badge without an action hint', () => {
    render(<StateBadge state="ARCHIVED" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveTextContent(/archived/i);
    expect(screen.queryByTestId('state-badge-hint')).not.toBeInTheDocument();
  });

  it('exposes a different data-state attribute per state for styling', () => {
    const { rerender } = render(<StateBadge state="DRAFT" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveAttribute('data-state', 'DRAFT');
    rerender(<StateBadge state="LOCKED" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveAttribute('data-state', 'LOCKED');
    rerender(<StateBadge state="RECONCILED" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveAttribute('data-state', 'RECONCILED');
    rerender(<StateBadge state="ARCHIVED" isReconcileEligible={false} />);
    expect(screen.getByTestId('state-badge')).toHaveAttribute('data-state', 'ARCHIVED');
  });
});
