import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import { WeeklyCommitModule } from './WeeklyCommitModule';

describe('WeeklyCommitModule', () => {
  it('renders the placeholder route at /weekly-commit', () => {
    render(
      <MemoryRouter initialEntries={['/weekly-commit']}>
        <WeeklyCommitModule />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('weekly-commit-root')).toBeInTheDocument();
    expect(screen.getByTestId('version')).toHaveTextContent(/Build:/);
  });
});
