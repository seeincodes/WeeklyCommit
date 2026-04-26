import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface DraftFormState {
  editingCommitId: string | null;
  dirty: boolean;
  optimisticOrder: string[] | null;
}

const initialState: DraftFormState = {
  editingCommitId: null,
  dirty: false,
  optimisticOrder: null,
};

export const draftFormSlice = createSlice({
  name: 'draftForm',
  initialState,
  reducers: {
    startEditing(state, action: PayloadAction<{ commitId: string }>) {
      state.editingCommitId = action.payload.commitId;
      state.dirty = false;
    },
    cancelEditing(state) {
      state.editingCommitId = null;
      state.dirty = false;
    },
    markDirty(state) {
      state.dirty = true;
    },
    reorder(state, action: PayloadAction<{ ids: string[] }>) {
      state.optimisticOrder = action.payload.ids;
    },
    commitReorder(state) {
      state.optimisticOrder = null;
    },
    rollbackReorder(state) {
      state.optimisticOrder = null;
    },
  },
});

export const draftFormActions = draftFormSlice.actions;

interface Root {
  draftForm: DraftFormState;
}

export const selectEditingCommitId = (s: Root) => s.draftForm.editingCommitId;
export const selectIsDirty = (s: Root) => s.draftForm.dirty;
export const selectOptimisticOrder = (s: Root) => s.draftForm.optimisticOrder;
