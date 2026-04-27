import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ReflectionField } from './ReflectionField';

describe('<ReflectionField />', () => {
  it('renders a labeled textarea with the current value', () => {
    render(<ReflectionField value="Past reflections" onChange={vi.fn()} />);
    expect(screen.getByRole('textbox', { name: /reflection/i })).toHaveValue('Past reflections');
  });

  it('calls onChange with the new text on each keystroke', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ReflectionField value="" onChange={onChange} />);

    await user.type(screen.getByRole('textbox', { name: /reflection/i }), 'X');

    expect(onChange).toHaveBeenCalledOnce();
    expect(onChange).toHaveBeenCalledWith('X');
  });

  it('shows the character counter as "<used>/500" while under the soft-warning threshold', () => {
    render(<ReflectionField value="hello" onChange={vi.fn()} />);
    expect(screen.getByTestId('reflection-counter')).toHaveTextContent('5/500');
    expect(screen.getByTestId('reflection-counter')).toHaveAttribute('data-warn-level', 'none');
  });

  it('caps input at 500 characters via maxLength', () => {
    render(<ReflectionField value="" onChange={vi.fn()} />);
    const ta = screen.getByRole('textbox', { name: /reflection/i });
    expect(ta).toHaveAttribute('maxLength', '500');
  });

  it('flips the counter to a warning style at >= 480 chars', () => {
    const value = 'a'.repeat(480);
    render(<ReflectionField value={value} onChange={vi.fn()} />);
    expect(screen.getByTestId('reflection-counter')).toHaveAttribute('data-warning', 'true');
  });

  it('does NOT flag warning style at 479 chars', () => {
    const value = 'a'.repeat(479);
    render(<ReflectionField value={value} onChange={vi.fn()} />);
    expect(screen.getByTestId('reflection-counter')).toHaveAttribute('data-warning', 'false');
    expect(screen.getByTestId('reflection-counter')).toHaveAttribute('data-warn-level', 'none');
  });

  it('uses the SOFT (amber) warn level between 480 and 494 chars', () => {
    const value = 'a'.repeat(480);
    render(<ReflectionField value={value} onChange={vi.fn()} />);
    const counter = screen.getByTestId('reflection-counter');
    expect(counter).toHaveAttribute('data-warn-level', 'soft');
    // Counter copy switches to "X left" framing once warning kicks in.
    expect(counter).toHaveTextContent('20 left');
  });

  it('escalates to HARD (red) warn level at 495 chars', () => {
    const value = 'a'.repeat(495);
    render(<ReflectionField value={value} onChange={vi.fn()} />);
    const counter = screen.getByTestId('reflection-counter');
    expect(counter).toHaveAttribute('data-warn-level', 'hard');
    expect(counter).toHaveTextContent('5 left');
  });

  it('shows "0 left" at the cap', () => {
    const value = 'a'.repeat(500);
    render(<ReflectionField value={value} onChange={vi.fn()} />);
    expect(screen.getByTestId('reflection-counter')).toHaveTextContent('0 left');
  });
});
