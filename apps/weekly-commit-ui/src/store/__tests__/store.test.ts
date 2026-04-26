import { describe, expect, it } from 'vitest';
import { store, type RootState } from '../index';

describe('store', () => {
  it('wires the api reducer at wcApi', () => {
    const state = store.getState();
    expect(state).toHaveProperty('wcApi');
  });

  it('wires draftForm at root', () => {
    const state: RootState = store.getState();
    expect(state.draftForm).toEqual({
      editingCommitId: null,
      dirty: false,
      optimisticOrder: null,
    });
  });

  it('wires conflictToast at root', () => {
    const state: RootState = store.getState();
    expect(state.conflictToast).toEqual({ visible: false, code: null });
  });
});
