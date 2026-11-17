import { useEffect, useState } from 'react';
import {
  Activity,
  Ban,
  CircleCheck,
  CircleX,
  Clock,
  Gauge,
  LayoutDashboard,
  ListChecks,
  LoaderCircle,
  OctagonX,
  Server,
  SlidersHorizontal,
  SquarePlus,
  Users,
  X,
} from 'lucide-react';
import { useApp } from './context';
import { api } from './api';
import { duration, isHttpUrl, prettyJson, relTime, statusClass, statusLabel } from './utils';

const NAV = [
  { name: 'overview', label: 'Overview', Icon: LayoutDashboard },
  { name: 'jobs', label: 'Jobs', Icon: ListChecks },
  { name: 'workers', label: 'Workers', Icon: Server },
  { name: 'submit', label: 'Submit job', Icon: SquarePlus, write: true },
  { name: 'users', label: 'Users', Icon: Users, admin: true },
  { name: 'config', label: 'Configuration', Icon: SlidersHorizontal },
];

const navIcon = { size: 20, strokeWidth: 1.75, 'aria-hidden': true };

export function RefreshButton({ onClick }) {
  const { refresh } = useApp();
  return (
    <button className="button" type="button" onClick={onClick || refresh}>
      Refresh
    </button>
  );
}

export function StatusPill({ status }) {
  return <span className={`label ${statusClass(status)}`}>{statusLabel(status)}</span>;
}

const STATUS_ICONS = {
  pending: Clock,
  running: LoaderCircle,
  succeeded: CircleCheck,
  failed: CircleX,
  dead: OctagonX,
  cancelled: Ban,
};

export function StatusIcon({ status, size = 18 }) {
  const cls = statusClass(status);
  const Icon = STATUS_ICONS[cls] || Clock;
  return (
    <span className={`state-icon ${cls}`} title={statusLabel(status)}>
      <Icon size={size} strokeWidth={1.9} aria-label={statusLabel(status)} role="img" />
    </span>
  );
}

export function Sidebar() {
  const { page, isAdmin, config } = useApp();
  const items = NAV.filter((n) => (!n.admin || isAdmin) && (!n.write || isAdmin));
  const monitoring = [
    { label: 'Prometheus', Icon: Activity, href: config?.endpoints?.prometheus },
    { label: 'Grafana', Icon: Gauge, href: config?.endpoints?.grafana },
  ].filter((link) => isHttpUrl(link.href));
  return (
    <aside className="sidebar">
      <nav className="nav-list" aria-label="Main navigation">
        {items.map(({ name, label, Icon }) => (
          <a
            key={name}
            className={`nav-link${page === name ? ' active' : ''}`}
            href={`#/${name}`}
            aria-current={page === name ? 'page' : undefined}
          >
            <Icon {...navIcon} />
            {label}
          </a>
        ))}
      </nav>
      {monitoring.length > 0 && (
        <div className="nav-group">
          <div className="nav-heading">Monitoring</div>
          <nav className="nav-list" aria-label="Monitoring">
            {monitoring.map(({ label, Icon, href }) => (
              <a key={label} className="nav-link external" href={href} target="_blank" rel="noreferrer">
                <Icon {...navIcon} />
                {label}
              </a>
            ))}
          </nav>
        </div>
      )}
    </aside>
  );
}

export function Topbar() {
  const { signedIn, user, signOut } = useApp();
  return (
    <header className="topbar">
      <a className="brand" href="#/overview" aria-label="Orbit overview">
        <img className="logo" src="/logo-dark.svg" alt="Orbit" height="28" />
      </a>
      {signedIn && (
        <div className="topbar-account">
          {user && <span className="topbar-user">{user.username}</span>}
          <button className="topbar-action" type="button" onClick={() => signOut()}>
            Sign out
          </button>
        </div>
      )}
    </header>
  );
}

export function Toast({ toast, onHide }) {
  useEffect(() => {
    if (!toast) return undefined;
    const id = setTimeout(onHide, 4000);
    return () => clearTimeout(id);
  }, [toast, onHide]);
  if (!toast) return null;
  return (
    <div className={`toast ${toast.type}`} role="status" aria-live="polite">
      {toast.message}
    </div>
  );
}

function Detail({ label, children }) {
  return (
    <div className="detail-item">
      <span className="k">{label}</span>
      <span className="v">{children}</span>
    </div>
  );
}

const clock = (at) =>
  new Date(at).toLocaleTimeString([], {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3,
  });

const offset = (from, to) => {
  const d = duration(from, to);
  return d === '—' ? '' : `+${d}`;
};

function Timeline({ job }) {
  const origin = new Date(job.createdAt).getTime();
  const steps = [
    ['Created', job.createdAt],
    ['Scheduled', job.scheduledAt],
    ['Started', job.startedAt],
    ['Finished', job.finishedAt],
  ].filter(([, at]) => at);
  return (
    <>
      <p className="timeline-date">
        {new Date(job.createdAt).toLocaleDateString(undefined, { dateStyle: 'long' })}
      </p>
      <ol className="steps">
        {steps.map(([label, at], i) => (
          <li key={label}>
            <span className="step-label">{label}</span>
            <span className="mono">{clock(at)}</span>
            <span className="muted mono">{i === 0 ? relTime(at) : offset(origin, at)}</span>
          </li>
        ))}
      </ol>
    </>
  );
}

export function JobDetailDrawer({ id, onClose }) {
  const { refresh, showToast, isAdmin } = useApp();
  const [job, setJob] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) return undefined;
    let cancelled = false;
    setJob(null);
    setError('');
    api
      .job(id)
      .then((data) => {
        if (!cancelled) setJob(data);
      })
      .catch((e) => {
        if (!cancelled) setError(e.message || 'Unable to load job.');
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    if (!id) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [id, onClose]);

  const cancel = async () => {
    setBusy(true);
    try {
      setJob(await api.cancel(job.id));
      showToast('Job cancelled.', 'success');
      refresh();
    } catch (e) {
      showToast(e.message || 'Unable to cancel job.', 'error');
    } finally {
      setBusy(false);
    }
  };

  const open = Boolean(id);
  const cancellable = isAdmin && job && ['PENDING', 'RUNNING'].includes(String(job.status).toUpperCase());
  return (
    <>
      <div className={`overlay${open ? ' open' : ''}`} onClick={onClose} aria-hidden="true" />
      <aside
        className={`drawer${open ? ' open' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label="Job details"
        aria-hidden={!open}
      >
        <div className="drawer-header">
          {job ? (
            <div className="drawer-title">
              <StatusIcon status={job.status} size={22} />
              <div>
                <h2>{job.taskType}</h2>
                <span className="mono muted">{job.id}</span>
              </div>
            </div>
          ) : (
            <h2>Job details</h2>
          )}
          <button className="icon-button" type="button" onClick={onClose} aria-label="Close">
            <X size={20} strokeWidth={1.75} aria-hidden="true" />
          </button>
        </div>
        <div className="drawer-body">
          {error ? (
            <div className="empty-state">{error}</div>
          ) : !job ? (
            <div className="empty-state">Loading…</div>
          ) : (
            <>
              <div className="detail-grid">
                <Detail label="Status">{statusLabel(job.status)}</Detail>
                <Detail label="Duration">{duration(job.startedAt, job.finishedAt)}</Detail>
                <Detail label="Tenant">{job.tenantId}</Detail>
                <Detail label="Priority">{job.priority}</Detail>
                <Detail label="Attempts">
                  {job.attempts} of {job.maxAttempts}
                </Detail>
                {job.workerId && (
                  <Detail label="Worker">
                    <span className="mono">{job.workerId}</span>
                  </Detail>
                )}
              </div>
              <h3 className="drawer-section">Timeline</h3>
              <Timeline job={job} />
              <h3 className="drawer-section">Payload</h3>
              <pre className="code">{prettyJson(job.payloadJson)}</pre>
              <h3 className="drawer-section">Output</h3>
              <pre className="code">{prettyJson(job.outputJson)}</pre>
              {job.lastError ? (
                <>
                  <h3 className="drawer-section">Last error</h3>
                  <pre className="code error">{job.lastError}</pre>
                </>
              ) : null}
            </>
          )}
        </div>
        {cancellable && (
          <div className="drawer-footer">
            <button className="button danger" type="button" onClick={cancel} disabled={busy}>
              {busy ? 'Cancelling…' : 'Cancel job'}
            </button>
          </div>
        )}
      </aside>
    </>
  );
}
