import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

// Fallback when a 409 response omits the `code` field. v1 maps every 409
// to OptimisticLockException in GlobalExceptionHandler — if a future endpoint
// adds a different 409 reason this default becomes wrong.
export const DEFAULT_CONFLICT_CODE = 'CONFLICT_OPTIMISTIC_LOCK';

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
