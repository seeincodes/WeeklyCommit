import { describe, expect, it } from 'vitest';
import {
  conflictToastSlice,
  conflictToastActions,
  selectConflictToast,
} from '../conflictToastSlice';

describe('conflictToastSlice', () => {
  it('starts hidden', () => {
    const state = conflictToastSlice.reducer(undefined, { type: '@@INIT' });
    expect(state).toEqual({ visible: false, code: null });
  });

  it('show() sets visible=true and stores the code', () => {
    const state = conflictToastSlice.reducer(
      { visible: false, code: null },
      conflictToastActions.show({ code: 'CONFLICT_OPTIMISTIC_LOCK' }),
    );
    expect(state).toEqual({ visible: true, code: 'CONFLICT_OPTIMISTIC_LOCK' });
  });

  it('hide() clears the code', () => {
    const state = conflictToastSlice.reducer(
      { visible: true, code: 'CONFLICT_OPTIMISTIC_LOCK' },
      conflictToastActions.hide(),
    );
    expect(state).toEqual({ visible: false, code: null });
  });

  it('selector returns the slice', () => {
    expect(selectConflictToast({ conflictToast: { visible: true, code: 'X' } })).toEqual({
      visible: true,
      code: 'X',
    });
  });
});
