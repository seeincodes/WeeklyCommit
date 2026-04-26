import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface ConflictToastState {
  visible: boolean;
  code: string | null;
}

const initialState: ConflictToastState = { visible: false, code: null };

export const conflictToastSlice = createSlice({
  name: 'conflictToast',
  initialState,
  reducers: {
    show(state, action: PayloadAction<{ code: string }>) {
      state.visible = true;
      state.code = action.payload.code;
    },
    hide(state) {
      state.visible = false;
      state.code = null;
    },
  },
});

export const conflictToastActions = conflictToastSlice.actions;

export const selectConflictToast = (state: { conflictToast: ConflictToastState }) =>
  state.conflictToast;
