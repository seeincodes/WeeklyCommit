import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store } from './store';
import { ConflictToast } from './components/ConflictToast';

// Route-level code splitting. Each page becomes its own chunk so the
// initial federated bundle only carries the shell + router + first
// route's dependencies. Pages are named exports, so re-shape them to
// `default` at the import site -- avoids touching the page files
// (which would force every test to rewrite imports) while still
// letting Vite/Rollup tree-split them.
const CurrentWeekPage = lazy(() =>
  import('./routes/CurrentWeekPage').then((m) => ({ default: m.CurrentWeekPage })),
);
const HistoryPage = lazy(() =>
  import('./routes/HistoryPage').then((m) => ({ default: m.HistoryPage })),
);
const TeamPage = lazy(() => import('./routes/TeamPage').then((m) => ({ default: m.TeamPage })));
const TeamMemberPage = lazy(() =>
  import('./routes/TeamMemberPage').then((m) => ({ default: m.TeamMemberPage })),
);

/**
 * Top-level federated component exposed to the PA host as
 * `weekly_commit/WeeklyCommitModule`. The host mounts this somewhere
 * inside its own <BrowserRouter> -- we use relative <Routes> so the
 * host's router context resolves the paths.
 *
 * Owns its own Redux store so the PA host's Redux tree (if any) doesn't
 * collide with this remote's. v1 places the Provider here rather than
 * higher up so both standalone-dev and federated paths inherit it without
 * duplication.
 */
export function WeeklyCommitModule() {
  return (
    <Provider store={store}>
      {/*
        Single Suspense boundary at the route level. A boundary per route
        would render the same fallback four times if the user clicked
        through routes during the initial chunk fetch -- one shared
        boundary is the simpler, correct shape. Fallback uses a testid
        so unit tests can assert "skeleton appeared" if they care.
      */}
      <Suspense fallback={<div data-testid="route-loading" aria-busy="true" />}>
        <Routes>
          <Route path="weekly-commit">
            <Route index element={<Navigate to="current" replace />} />
            <Route path="current" element={<CurrentWeekPage />} />
            <Route path="history" element={<HistoryPage />} />
            <Route path="team" element={<TeamPage />} />
            <Route path="team/:employeeId" element={<TeamMemberPage />} />
          </Route>
        </Routes>
      </Suspense>
      {/*
        Singleton 409-conflict toast. Lives inside the Provider so it can
        read the conflictToast slice; outside the Routes so it persists
        across navigations. Fixed-positioned (bottom-right) and stays out
        of any layout flow until something dispatches conflictToastActions.show.
      */}
      <ConflictToast />
    </Provider>
  );
}
