import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import { draftFormSlice } from '../store/draftFormSlice';
import { ReviewCommentField } from './ReviewCommentField';

const mockUseCreateReviewMutation = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useCreateReviewMutation: mockUseCreateReviewMutation,
  };
});

function renderField(props: Partial<Parameters<typeof ReviewCommentField>[0]> = {}) {
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
      <ReviewCommentField planId="plan-1" managerReviewedAt={undefined} {...props} />
    </Provider>,
  );
}

describe('<ReviewCommentField />', () => {
  beforeEach(() => {
    mockUseCreateReviewMutation.mockReset();
  });

  it('renders a labeled textarea + submit button', () => {
    mockUseCreateReviewMutation.mockReturnValue([vi.fn(), { isLoading: false }]);
    renderField();
    expect(screen.getByRole('textbox', { name: /comment/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /acknowledge/i })).toBeInTheDocument();
  });

  it('disables the submit button when the comment is empty', () => {
    mockUseCreateReviewMutation.mockReturnValue([vi.fn(), { isLoading: false }]);
    renderField();
    expect(screen.getByRole('button', { name: /acknowledge/i })).toBeDisabled();
  });

  it('enables the submit button when the comment has content', async () => {
    const user = userEvent.setup();
    mockUseCreateReviewMutation.mockReturnValue([vi.fn(), { isLoading: false }]);
    renderField();

    await user.type(screen.getByRole('textbox', { name: /comment/i }), 'Nice work this week.');
    expect(screen.getByRole('button', { name: /acknowledge/i })).toBeEnabled();
  });

  it('calls the mutation with planId + comment body on submit', async () => {
    const user = userEvent.setup();
    const mutate = vi.fn().mockReturnValue({ unwrap: vi.fn().mockResolvedValue({}) });
    mockUseCreateReviewMutation.mockReturnValue([mutate, { isLoading: false }]);
    renderField({ planId: 'plan-99' });

    await user.type(screen.getByRole('textbox', { name: /comment/i }), 'Great Top Rock pick.');
    await user.click(screen.getByRole('button', { name: /acknowledge/i }));

    expect(mutate).toHaveBeenCalledOnce();
    expect(mutate).toHaveBeenCalledWith({
      planId: 'plan-99',
      body: { comment: 'Great Top Rock pick.' },
    });
  });

  it('clears the textarea on successful submission', async () => {
    const user = userEvent.setup();
    const mutate = vi.fn().mockReturnValue({ unwrap: vi.fn().mockResolvedValue({}) });
    mockUseCreateReviewMutation.mockReturnValue([mutate, { isLoading: false }]);
    renderField();

    const textarea = screen.getByRole('textbox', { name: /comment/i });
    await user.type(textarea, 'Acknowledged.');
    await user.click(screen.getByRole('button', { name: /acknowledge/i }));

    expect(textarea).toHaveValue('');
  });

  it('shows the loading state while the mutation is in flight', () => {
    mockUseCreateReviewMutation.mockReturnValue([vi.fn(), { isLoading: true }]);
    renderField();
    expect(screen.getByRole('button', { name: /acknowledging/i })).toBeDisabled();
  });

  it('surfaces an error message when the mutation rejects', async () => {
    const user = userEvent.setup();
    const mutate = vi
      .fn()
      .mockReturnValue({ unwrap: vi.fn().mockRejectedValue(new Error('server boom')) });
    mockUseCreateReviewMutation.mockReturnValue([mutate, { isLoading: false }]);
    renderField();

    await user.type(screen.getByRole('textbox', { name: /comment/i }), 'A note.');
    await user.click(screen.getByRole('button', { name: /acknowledge/i }));

    expect(await screen.findByTestId('review-comment-error')).toBeInTheDocument();
  });

  it('shows the "reviewed at" indicator when managerReviewedAt is set', () => {
    mockUseCreateReviewMutation.mockReturnValue([vi.fn(), { isLoading: false }]);
    renderField({ managerReviewedAt: '2026-05-04T12:00:00Z' });
    expect(screen.getByTestId('review-acknowledged-at')).toBeInTheDocument();
  });

  it('does NOT show the "reviewed at" indicator when managerReviewedAt is undefined', () => {
    mockUseCreateReviewMutation.mockReturnValue([vi.fn(), { isLoading: false }]);
    renderField();
    expect(screen.queryByTestId('review-acknowledged-at')).not.toBeInTheDocument();
  });
});
