import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import { WeeklyCommitModule } from './WeeklyCommitModule';

describe('WeeklyCommitModule routing', () => {
  it('renders the current-week page at /weekly-commit/current', () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/current']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('current-week-page')).toBeInTheDocument();
  });

  it('renders the history page at /weekly-commit/history', () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/history']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('history-page')).toBeInTheDocument();
  });

  it('redirects /weekly-commit (no sub-route) to /weekly-commit/current', () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    // The redirect target's marker is the current-week-page testid.
    expect(screen.getByTestId('current-week-page')).toBeInTheDocument();
  });

  it('renders the team rollup page at /weekly-commit/team', () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/team']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('team-page')).toBeInTheDocument();
  });

  it('renders the team member page at /weekly-commit/team/:employeeId with the id surfaced', () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit/team/emp-42']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('team-member-page')).toBeInTheDocument();
    expect(screen.getByTestId('team-member-id')).toHaveTextContent('emp-42');
  });
});
