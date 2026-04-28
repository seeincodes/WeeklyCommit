import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import { BlankState } from './BlankState';

// Mocks the one RTK Query hook BlankState uses
// (`useCreateCurrentForMeMutation`) directly, matching the
// DraftMode/ReconcileMode test pattern -- no MSW server in this codebase.
const mockCreatePlanTrigger = vi.hoisted(() => vi.fn());
const mockUseCreateCurrentForMeMutation = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useCreateCurrentForMeMutation: mockUseCreateCurrentForMeMutation,
  };
});

function renderWithStore(ui: React.ReactNode) {
  const store = configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  return render(<Provider store={store}>{ui}</Provider>);
}

describe('<BlankState />', () => {
  beforeEach(() => {
    mockCreatePlanTrigger.mockReset();
    mockUseCreateCurrentForMeMutation.mockReset();
    mockUseCreateCurrentForMeMutation.mockReturnValue([
      mockCreatePlanTrigger,
      { isLoading: false, error: undefined },
    ]);
  });

  it('renders the empty-state surface with the create CTA', () => {
    renderWithStore(<BlankState />);
    expect(screen.getByTestId('week-editor-blank')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /ready to plan your week/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create plan/i })).toBeEnabled();
  });

  it('renders the illustrative tier-glyph cluster', () => {
    renderWithStore(<BlankState />);
    expect(screen.getByTestId('blank-state-illustration')).toBeInTheDocument();
  });

  it('fires the create-plan mutation when the CTA is clicked', async () => {
    const user = userEvent.setup();
    renderWithStore(<BlankState />);
    await user.click(screen.getByRole('button', { name: /create plan/i }));
    expect(mockCreatePlanTrigger).toHaveBeenCalledTimes(1);
  });

  it('shows a "Creating…" label and disables the button while in flight', () => {
    mockUseCreateCurrentForMeMutation.mockReturnValue([
      mockCreatePlanTrigger,
      { isLoading: true, error: undefined },
    ]);
    renderWithStore(<BlankState />);
    const button = screen.getByRole('button', { name: /creating/i });
    expect(button).toBeDisabled();
    expect(button).toHaveTextContent(/creating/i);
  });

  it('renders an error banner when the mutation fails', () => {
    mockUseCreateCurrentForMeMutation.mockReturnValue([
      mockCreatePlanTrigger,
      { isLoading: false, error: { status: 500, data: { error: { code: 'INTERNAL' } } } },
    ]);
    renderWithStore(<BlankState />);
    expect(screen.getByTestId('blank-state-error')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/couldn.{1,3}t create/i);
  });
});
