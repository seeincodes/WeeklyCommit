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
});
