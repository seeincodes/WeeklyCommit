import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { conflictToastActions, conflictToastSlice, api } from '@wc/rtk-api-client';
import { ConflictToast } from './ConflictToast';

function makeStore() {
  return configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
}

function renderToast(autoDismissMs?: number) {
  const store = makeStore();
  const ui = render(
    <Provider store={store}>
      <ConflictToast {...(autoDismissMs != null ? { autoDismissMs } : {})} />
    </Provider>,
  );
  return { store, ...ui };
}

describe('<ConflictToast />', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders nothing while the conflict slice is hidden', () => {
    renderToast();
    expect(screen.queryByTestId('conflict-toast')).not.toBeInTheDocument();
  });

  it('renders the optimistic-lock copy when the slice fires the default code', () => {
    const { store } = renderToast(0);
    act(() => {
      store.dispatch(conflictToastActions.show({ code: 'CONFLICT_OPTIMISTIC_LOCK' }));
    });
    expect(screen.getByTestId('conflict-toast')).toBeInTheDocument();
    expect(screen.getByTestId('conflict-toast-headline')).toHaveTextContent(
      /refreshed in the background/i,
    );
    expect(screen.getByTestId('conflict-toast-detail')).toHaveTextContent(
      /another tab updated this plan/i,
    );
  });

  it('renders fallback copy for an unknown conflict code', () => {
    const { store } = renderToast(0);
    act(() => {
      store.dispatch(conflictToastActions.show({ code: 'FUTURE_CODE_WE_HAVE_NOT_MAPPED' }));
    });
    expect(screen.getByTestId('conflict-toast-detail')).toHaveTextContent(
      /something changed under us/i,
    );
  });

  it('auto-dismisses after the configured window', () => {
    const { store } = renderToast(2_000);
    act(() => {
      store.dispatch(conflictToastActions.show({ code: 'CONFLICT_OPTIMISTIC_LOCK' }));
    });
    expect(screen.getByTestId('conflict-toast')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(2_001);
    });
    expect(screen.queryByTestId('conflict-toast')).not.toBeInTheDocument();
  });

  it('does not auto-dismiss when autoDismissMs is 0 (test-friendly mode)', () => {
    const { store } = renderToast(0);
    act(() => {
      store.dispatch(conflictToastActions.show({ code: 'CONFLICT_OPTIMISTIC_LOCK' }));
    });
    expect(screen.getByTestId('conflict-toast')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(60_000);
    });
    // Still visible -- no timer was set.
    expect(screen.getByTestId('conflict-toast')).toBeInTheDocument();
  });

  it('renders Flowbite ToastToggle close button for impatient users', () => {
    // Flowbite's <ToastToggle /> manages its own dismissed state internally
    // (separate from our Redux slice). For our v1 contract, what matters is
    // that the manual-close affordance is rendered and accessible -- the
    // Redux slice gets cleaned up by the auto-dismiss path regardless.
    const { store } = renderToast(0);
    act(() => {
      store.dispatch(conflictToastActions.show({ code: 'CONFLICT_OPTIMISTIC_LOCK' }));
    });
    expect(screen.getByRole('button', { name: /close/i })).toBeInTheDocument();
  });

  it('exposes role=status + aria-live=polite for assistive tech', () => {
    const { store } = renderToast(0);
    act(() => {
      store.dispatch(conflictToastActions.show({ code: 'CONFLICT_OPTIMISTIC_LOCK' }));
    });
    const toast = screen.getByTestId('conflict-toast');
    expect(toast).toHaveAttribute('role', 'status');
    expect(toast).toHaveAttribute('aria-live', 'polite');
  });
});
