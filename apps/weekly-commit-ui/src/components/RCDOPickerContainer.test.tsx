import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import type { SupportingOutcome } from '@wc/rtk-api-client';
import { draftFormSlice } from '../store/draftFormSlice';
import { RCDOPickerContainer } from './RCDOPickerContainer';

const mockUseGetSupportingOutcomesQuery = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useGetSupportingOutcomesQuery: mockUseGetSupportingOutcomesQuery,
  };
});

function renderWithStore() {
  const store = configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
      draftForm: draftFormSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  return render(
    <Provider store={store}>
      <RCDOPickerContainer onSelect={vi.fn()} />
    </Provider>,
  );
}

const SAMPLE: SupportingOutcome = {
  id: 'so_1',
  label: 'Alignment tooling GA',
  active: true,
  breadcrumb: {
    rallyCry: { id: 'rc', label: 'Unblock product-led growth' },
    definingObjective: { id: 'do', label: 'Product-led GTM' },
    coreOutcome: { id: 'co', label: 'Tooling readiness' },
    supportingOutcome: { id: 'so_1', label: 'Alignment tooling GA' },
  },
};

describe('<RCDOPickerContainer />', () => {
  beforeEach(() => {
    mockUseGetSupportingOutcomesQuery.mockReset();
  });

  it('renders a loading indicator while the query is in flight', () => {
    mockUseGetSupportingOutcomesQuery.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
    });
    renderWithStore();
    expect(screen.getByTestId('rcdo-picker-loading')).toBeInTheDocument();
  });

  it('renders the picker with fetched outcomes on success', () => {
    mockUseGetSupportingOutcomesQuery.mockReturnValue({
      data: [SAMPLE],
      error: undefined,
      isLoading: false,
    });
    renderWithStore();
    expect(screen.getByTestId('rcdo-picker')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /alignment tooling ga/i })).toBeInTheDocument();
    // Stale banner should NOT appear on the success path.
    expect(screen.queryByTestId('rcdo-picker-stale-banner')).not.toBeInTheDocument();
  });

  it('renders the picker with the stale-cache banner when the query errors', () => {
    mockUseGetSupportingOutcomesQuery.mockReturnValue({
      data: undefined,
      error: { status: 502, data: { code: 'UPSTREAM_ERROR' } },
      isLoading: false,
    });
    renderWithStore();
    expect(screen.getByTestId('rcdo-picker')).toBeInTheDocument();
    expect(screen.getByTestId('rcdo-picker-stale-banner')).toBeInTheDocument();
  });

  it('keeps the picker mounted with stale data when the refetch errors but cached data is present', () => {
    // RTK Query surfaces last-known data on `data` AND error on `error` during a failed refetch.
    mockUseGetSupportingOutcomesQuery.mockReturnValue({
      data: [SAMPLE],
      error: { status: 502, data: { code: 'UPSTREAM_ERROR' } },
      isLoading: false,
    });
    renderWithStore();
    expect(screen.getByRole('option', { name: /alignment tooling ga/i })).toBeInTheDocument();
    expect(screen.getByTestId('rcdo-picker-stale-banner')).toBeInTheDocument();
  });
});
