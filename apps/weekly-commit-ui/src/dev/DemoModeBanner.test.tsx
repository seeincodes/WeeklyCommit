import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DemoModeBanner } from './DemoModeBanner';

describe('<DemoModeBanner />', () => {
  let assignSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    assignSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        href: 'https://demo.example.com/?devRole=MANAGER#/weekly-commit/current',
        assign: assignSpy,
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders with the active identity surfaced', () => {
    render(<DemoModeBanner role="MANAGER" />);
    expect(screen.getByTestId('demo-mode-banner')).toBeInTheDocument();
    expect(screen.getByTestId('demo-mode-banner-identity')).toHaveTextContent(/Ada Lovelace/);
    expect(screen.getByTestId('demo-mode-banner-identity')).toHaveTextContent(/Manager/);
  });

  it('renders different identities per role', () => {
    const { rerender } = render(<DemoModeBanner role="IC" />);
    expect(screen.getByTestId('demo-mode-banner-identity')).toHaveTextContent(/Ben Carter/);

    rerender(<DemoModeBanner role="IC_NULL_MANAGER" />);
    expect(screen.getByTestId('demo-mode-banner-identity')).toHaveTextContent(/Frankie Hopper/);

    rerender(<DemoModeBanner role="ADMIN" />);
    expect(screen.getByTestId('demo-mode-banner-identity')).toHaveTextContent(/Site Admin/);
  });

  it('clears ?devRole= and reloads when "Switch identity" is clicked', async () => {
    const user = userEvent.setup();
    render(<DemoModeBanner role="MANAGER" />);

    await user.click(screen.getByTestId('demo-mode-banner-switch'));

    expect(assignSpy).toHaveBeenCalledTimes(1);
    const navUrl = assignSpy.mock.calls[0]?.[0] as string;
    expect(navUrl).not.toContain('devRole');
    // Hash route preserved -- a viewer mid-navigation gets sent back to the
    // picker without losing the route they were on (well, sort of -- the
    // URL holds it, but the picker takes over the page).
    expect(navUrl).toContain('#/weekly-commit/current');
  });
});
