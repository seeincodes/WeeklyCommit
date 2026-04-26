import { describe, expect, it } from 'vitest';
import {
  draftFormSlice,
  draftFormActions,
  selectEditingCommitId,
  selectIsDirty,
  selectOptimisticOrder,
} from '../draftFormSlice';

describe('draftFormSlice', () => {
  it('starts empty', () => {
    const state = draftFormSlice.reducer(undefined, { type: '@@INIT' });
    expect(state).toEqual({ editingCommitId: null, dirty: false, optimisticOrder: null });
  });

  it('startEditing sets the editing id and clears dirty', () => {
    const state = draftFormSlice.reducer(
      { editingCommitId: null, dirty: true, optimisticOrder: null },
      draftFormActions.startEditing({ commitId: 'c1' }),
    );
    expect(state.editingCommitId).toBe('c1');
    expect(state.dirty).toBe(false);
  });

  it('markDirty flips dirty=true', () => {
    const state = draftFormSlice.reducer(
      { editingCommitId: 'c1', dirty: false, optimisticOrder: null },
      draftFormActions.markDirty(),
    );
    expect(state.dirty).toBe(true);
  });

  it('reorder stores the optimistic id list', () => {
    const state = draftFormSlice.reducer(
      { editingCommitId: null, dirty: false, optimisticOrder: null },
      draftFormActions.reorder({ ids: ['c2', 'c1', 'c3'] }),
    );
    expect(state.optimisticOrder).toEqual(['c2', 'c1', 'c3']);
  });

  it('commitReorder clears the optimistic buffer (server confirmed)', () => {
    const state = draftFormSlice.reducer(
      { editingCommitId: null, dirty: false, optimisticOrder: ['c2', 'c1'] },
      draftFormActions.commitReorder(),
    );
    expect(state.optimisticOrder).toBeNull();
  });

  it('rollbackReorder clears the optimistic buffer (server rejected)', () => {
    const state = draftFormSlice.reducer(
      { editingCommitId: null, dirty: false, optimisticOrder: ['c2', 'c1'] },
      draftFormActions.rollbackReorder(),
    );
    expect(state.optimisticOrder).toBeNull();
  });

  it('selectors return the right slices', () => {
    const root = {
      draftForm: { editingCommitId: 'c1', dirty: true, optimisticOrder: ['c1', 'c2'] },
    };
    expect(selectEditingCommitId(root)).toBe('c1');
    expect(selectIsDirty(root)).toBe(true);
    expect(selectOptimisticOrder(root)).toEqual(['c1', 'c2']);
  });

  it('cancelEditing clears the editing id and dirty', () => {
    const state = draftFormSlice.reducer(
      { editingCommitId: 'c1', dirty: true, optimisticOrder: null },
      draftFormActions.cancelEditing(),
    );
    expect(state).toEqual({ editingCommitId: null, dirty: false, optimisticOrder: null });
  });
});
