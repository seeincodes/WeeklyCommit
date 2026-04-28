import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DemoLoginPicker } from './DemoLoginPicker';

/**
 * The picker's only side effect is `window.location.assign` on click. Every
 * test stubs that out so the JSDOM environment doesn't actually try to
 * navigate.
 */
describe('<DemoLoginPicker />', () => {
  let assignSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    assignSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        href: 'https://demo.example.com/',
        assign: assignSpy,
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders four role cards', () => {
    render(<DemoLoginPicker />);

    expect(screen.getByTestId('demo-login-picker')).toBeInTheDocument();
    expect(screen.getByTestId('demo-login-role-MANAGER')).toBeInTheDocument();
    expect(screen.getByTestId('demo-login-role-IC')).toBeInTheDocument();
    expect(screen.getByTestId('demo-login-role-IC_NULL_MANAGER')).toBeInTheDocument();
    expect(screen.getByTestId('demo-login-role-ADMIN')).toBeInTheDocument();
  });

  it('exposes each role with both a name and a title', () => {
    render(<DemoLoginPicker />);
    // The seeded names match DemoDataSeeder + the cypress test users -- if a
    // future drift renames "Ada" to something else, the test breaks loudly so
    // the demo-login-page copy gets updated alongside.
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('Ben Carter')).toBeInTheDocument();
    expect(screen.getByText('Frankie Hopper')).toBeInTheDocument();
    expect(screen.getByText('Site Admin')).toBeInTheDocument();
  });

  it('navigates to ?devRole=<role> on click', async () => {
    const user = userEvent.setup();
    render(<DemoLoginPicker />);

    await user.click(screen.getByTestId('demo-login-role-IC'));

    expect(assignSpy).toHaveBeenCalledTimes(1);
    expect(assignSpy.mock.calls[0]?.[0]).toContain('devRole=IC');
  });

  it('navigates to ?devRole=ADMIN for the admin card', async () => {
    const user = userEvent.setup();
    render(<DemoLoginPicker />);

    await user.click(screen.getByTestId('demo-login-role-ADMIN'));

    expect(assignSpy).toHaveBeenCalledWith(expect.stringContaining('devRole=ADMIN'));
  });

  it('preserves any existing query params when adding devRole', async () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        href: 'https://demo.example.com/?utm_source=portfolio',
        assign: assignSpy,
      },
    });
    const user = userEvent.setup();
    render(<DemoLoginPicker />);

    await user.click(screen.getByTestId('demo-login-role-MANAGER'));

    const navUrl = assignSpy.mock.calls[0]?.[0] as string;
    expect(navUrl).toContain('utm_source=portfolio');
    expect(navUrl).toContain('devRole=MANAGER');
  });
});
