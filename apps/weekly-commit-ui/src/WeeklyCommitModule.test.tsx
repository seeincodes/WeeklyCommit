import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import { WeeklyCommitModule } from './WeeklyCommitModule';

// Routes are lazy-loaded behind a Suspense boundary, so the page testid
// resolves on a microtask rather than synchronously. `findByTestId` polls
// up to its default timeout, which is the right shape for both the
// dynamic-import and Navigate-redirect cases.
describe('WeeklyCommitModule routing', () => {
  it('renders the current-week page at /weekly-commit/current', async () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/current']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(await screen.findByTestId('current-week-page')).toBeInTheDocument();
  });

  it('renders the history page at /weekly-commit/history', async () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/history']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(await screen.findByTestId('history-page')).toBeInTheDocument();
  });

  it('redirects /weekly-commit (no sub-route) to /weekly-commit/current', async () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    // The redirect target's marker is the current-week-page testid.
    expect(await screen.findByTestId('current-week-page')).toBeInTheDocument();
  });

  it('renders the team rollup page at /weekly-commit/team', async () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/team']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(await screen.findByTestId('team-page')).toBeInTheDocument();
  });

  it('renders the team member page at /weekly-commit/team/:employeeId with the id surfaced', async () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/team/emp-42']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(await screen.findByTestId('team-member-page')).toBeInTheDocument();
    expect(screen.getByTestId('team-member-id')).toHaveTextContent('emp-42');
  });
});
