import { Routes, Route, Navigate } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store } from './store';
import { CurrentWeekPage } from './routes/CurrentWeekPage';
import { HistoryPage } from './routes/HistoryPage';
import { TeamPage } from './routes/TeamPage';
import { TeamMemberPage } from './routes/TeamMemberPage';

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
      <Routes>
        <Route path="weekly-commit">
          <Route index element={<Navigate to="current" replace />} />
          <Route path="current" element={<CurrentWeekPage />} />
          <Route path="history" element={<HistoryPage />} />
          <Route path="team" element={<TeamPage />} />
          <Route path="team/:employeeId" element={<TeamMemberPage />} />
        </Route>
      </Routes>
    </Provider>
  );
}
