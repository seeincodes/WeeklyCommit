import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import { AppShell } from './AppShell';

// AppShell wraps every route's chrome (header / nav / footer). Pure
// presentational + react-router NavLink -- no RTK Query, no store. We
// render inside a MemoryRouter because the nav uses NavLink and would
// otherwise crash on missing router context. Asserts cover the slot
// contract (title, eyebrow, headerSlot, subnav, children) plus the
// build-stamp footer + nav structure.

function renderShell(props: Parameters<typeof AppShell>[0]) {
  return render(
    <MemoryRouter initialEntries={['/weekly-commit/current']}>
      <AppShell {...props} />
    </MemoryRouter>,
  );
}

describe('<AppShell />', () => {
  it('renders the title and the children slot', () => {
    renderShell({
      title: 'This week',
      children: <div data-testid="page-body">page contents</div>,
    });
    expect(screen.getByRole('heading', { level: 1, name: 'This week' })).toBeInTheDocument();
    expect(screen.getByTestId('page-body')).toBeInTheDocument();
  });

  it('renders the eyebrow when supplied', () => {
    renderShell({
      eyebrow: 'Plan & lock',
      title: 'This week',
      children: <span />,
    });
    expect(screen.getByText('Plan & lock')).toBeInTheDocument();
  });

  it('omits the eyebrow when the prop is empty (treats "" as absent)', () => {
    renderShell({
      eyebrow: '',
      title: 'This week',
      children: <span />,
    });
    // No eyebrow span means no uppercase-meta neighbour to the heading.
    expect(screen.queryByText('Plan & lock')).not.toBeInTheDocument();
  });

  it('renders the headerSlot in the right rail', () => {
    renderShell({
      title: 'Team',
      headerSlot: <span data-testid="header-status">LOCKED</span>,
      children: <span />,
    });
    expect(screen.getByTestId('header-status')).toHaveTextContent('LOCKED');
  });

  it('renders the subnav slot under the title row', () => {
    renderShell({
      title: 'Team',
      subnav: <nav data-testid="filter-row">filters</nav>,
      children: <span />,
    });
    expect(screen.getByTestId('filter-row')).toBeInTheDocument();
  });

  it('exposes the testId on the outer wrapper for routing tests to grep', () => {
    renderShell({
      title: 'This week',
      testId: 'current-week-page',
      children: <span />,
    });
    expect(screen.getByTestId('current-week-page')).toBeInTheDocument();
  });

  it('renders the three primary nav links', () => {
    renderShell({ title: 'Anything', children: <span /> });
    // Use aria-label so the assertion survives nav-style refactors.
    const nav = screen.getByRole('navigation', { name: /weekly commit/i });
    expect(nav).toHaveTextContent(/this week/i);
    expect(nav).toHaveTextContent(/history/i);
    expect(nav).toHaveTextContent(/team/i);
  });

  it('renders the build-stamp footer with the injected SHA', () => {
    renderShell({ title: 'Anything', children: <span /> });
    expect(screen.getByTestId('app-shell-footer')).toBeInTheDocument();
    // vite.config.ts defines __WC_GIT_SHA__ → 'dev' under tests.
    expect(screen.getByTestId('version')).toHaveTextContent(/Build:\s*dev/);
  });
});
