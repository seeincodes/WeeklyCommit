import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query/react';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import { draftFormSlice } from './draftFormSlice';

export const store = configureStore({
  reducer: {
    [api.reducerPath]: api.reducer,
    conflictToast: conflictToastSlice.reducer,
    draftForm: draftFormSlice.reducer,
  },
  middleware: (getDefault) => getDefault().concat(api.middleware),
});

setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
