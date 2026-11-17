import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppContext } from './context';
import { api, getStoredUser, getToken, isAuthError, setStoredUser, setToken } from './api';
import { Sidebar, Topbar, Toast, JobDetailDrawer } from './components';
import { Login } from './pages/Login';
import { Overview } from './pages/Overview';
import { Jobs } from './pages/Jobs';
import { Workers } from './pages/Workers';
import { SubmitJob } from './pages/SubmitJob';
import { Users } from './pages/Users';
import { Configuration } from './pages/Configuration';

const PAGES = {
  overview: Overview,
  jobs: Jobs,
  workers: Workers,
  submit: SubmitJob,
  users: Users,
  config: Configuration,
};

const PAGE_TITLES = {
  overview: 'Overview',
  jobs: 'Jobs',
  workers: 'Workers',
  submit: 'Submit job',
  users: 'Users',
  config: 'Configuration',
};

const REFRESH_MS = 10000;

function useHashRoute() {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  const page = hash.replace(/^#\/?/, '') || 'overview';
  return page in PAGES ? page : 'overview';
}

export default function App() {
  const page = useHashRoute();
  const [signedIn, setSignedIn] = useState(() => Boolean(getToken()));
  const [user, setUser] = useState(() => getStoredUser());
  const [sessionNote, setSessionNote] = useState('');
  const [stats, setStats] = useState({});
  const [jobs, setJobs] = useState([]);
  const [workers, setWorkers] = useState([]);
  const [config, setConfig] = useState(null);
  const [toast, setToast] = useState(null);
  const [detailId, setDetailId] = useState(null);

  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    document.title = signedIn ? PAGE_TITLES[page] : 'Sign in';
  }, [page, signedIn]);

  const showToast = useCallback((message, type = '') => setToast(message ? { message, type } : null), []);
  const hideToast = useCallback(() => setToast(null), []);
  const closeDetail = useCallback(() => setDetailId(null), []);

  const clearSession = useCallback((note = '') => {
    setToken('');
    setStoredUser(null);
    setUser(null);
    setSignedIn(false);
    setSessionNote(note);
    setDetailId(null);
    setStats({});
    setJobs([]);
    setWorkers([]);
    setConfig(null);
  }, []);

  const signOut = useCallback(async () => {
    await api.logout();
    clearSession();
  }, [clearSession]);

  const signIn = useCallback(async (username, password) => {
    if (!username.trim() || !password) return 'Enter your username and password.';
    try {
      const result = await api.login(username.trim(), password);
      setToken(result.token);
      setStoredUser(result.user);
      setUser(result.user);
      setSessionNote('');
      setSignedIn(true);
      return '';
    } catch (e) {
      if (e.status === 401 || e.status === 429) return e.message;
      return 'Could not reach the scheduler. Check that it is running.';
    }
  }, []);

  const refresh = useCallback(async () => {
    if (!getToken()) return;
    try {
      const [s, j, w, c] = await Promise.all([api.stats(), api.jobs(), api.workers(), api.config()]);
      setStats(s);
      setJobs(Array.isArray(j) ? j : []);
      setWorkers(Array.isArray(w) ? w : []);
      setConfig(c);
    } catch (e) {
      if (isAuthError(e)) clearSession('Your session has ended. Sign in again.');
      else showToast(e.message || 'Unable to load data.', 'error');
    }
  }, [showToast, clearSession]);

  useEffect(() => {
    if (!signedIn) return undefined;
    refresh();
    const id = setInterval(refresh, REFRESH_MS);
    return () => clearInterval(id);
  }, [signedIn, refresh]);

  useEffect(() => {
    if (!signedIn) return;
    api
      .me()
      .then((me) => {
        const next = { username: me.username, role: me.role };
        setUser(next);
        setStoredUser(next);
      })
      .catch(() => undefined);
  }, [signedIn]);

  const value = useMemo(
    () => ({
      page,
      signedIn,
      user,
      isAdmin,
      stats,
      jobs,
      workers,
      config,
      refresh,
      signIn,
      signOut,
      showToast,
      setDetailId,
    }),
    [page, signedIn, user, isAdmin, stats, jobs, workers, config, refresh, signIn, signOut, showToast],
  );

  const restricted = page === 'users' && !isAdmin;
  const Page = restricted ? Overview : PAGES[page];
  return (
    <AppContext.Provider value={value}>
      {signedIn && <Topbar />}
      {signedIn ? (
        <>
          <Sidebar />
          <main className="main-content">
            <Page />
          </main>
          <JobDetailDrawer id={detailId} onClose={closeDetail} />
        </>
      ) : (
        <Login note={sessionNote} />
      )}
      <Toast toast={toast} onHide={hideToast} />
    </AppContext.Provider>
  );
}
