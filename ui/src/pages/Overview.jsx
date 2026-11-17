import { useApp } from '../context';
import { RefreshButton, StatusPill } from '../components';
import { JOB_STATUSES, number, relTime, statusClass, statusLabel } from '../utils';

const MINUTE = 60 * 1000;
const WINDOWS = [
  { ms: 30 * MINUTE, label: '30 minutes' },
  { ms: 2 * 60 * MINUTE, label: '2 hours' },
  { ms: 12 * 60 * MINUTE, label: '12 hours' },
  { ms: 7 * 24 * 60 * MINUTE, label: '7 days' },
];
const BUCKETS = 30;
const LIST_LIMIT = 6;

const pct = (part, whole) => (whole > 0 ? (part / whole) * 100 : 0);
const isFailed = (s) => ['FAILED', 'DEAD'].includes(String(s).toUpperCase());
const isOnline = (w) => String(w.status).toUpperCase() === 'ONLINE';
const age = (job, now) => now - new Date(job.createdAt).getTime();

function pickWindow(jobs, now) {
  return WINDOWS.find((w) => jobs.some((j) => age(j, now) >= 0 && age(j, now) < w.ms)) || WINDOWS[0];
}

function bucketJobs(jobs, now, windowMs) {
  const buckets = Array.from({ length: BUCKETS }, () => ({ ok: 0, failed: 0 }));
  for (const job of jobs) {
    const index = Math.floor(age(job, now) / (windowMs / BUCKETS));
    if (index < 0 || index >= BUCKETS) continue;
    buckets[BUCKETS - 1 - index][isFailed(job.status) ? 'failed' : 'ok'] += 1;
  }
  return buckets;
}

function Panel({ title, note, action, className = '', children }) {
  return (
    <section className={`panel ${className}`}>
      <header className="panel-header">
        <h2 className="panel-title">{title}</h2>
        {note && <span className="panel-note">{note}</span>}
        {action}
      </header>
      {children}
    </section>
  );
}

function Kpi({ label, value, bad }) {
  return (
    <div className="kpi">
      <dt>{label}</dt>
      <dd className={bad ? 'bad' : undefined}>{value}</dd>
    </div>
  );
}

function Throughput({ jobs }) {
  const now = Date.now();
  const range = pickWindow(jobs, now);
  const buckets = bucketJobs(jobs, now, range.ms);
  const created = buckets.reduce((sum, b) => sum + b.ok + b.failed, 0);
  const peak = Math.max(1, ...buckets.map((b) => b.ok + b.failed));
  return (
    <Panel title="Jobs created" note={`${number(created)} in the last ${range.label}`} className="span-8">
      <div className="chart" role="img" aria-label={`${created} jobs created in the last ${range.label}`}>
        {buckets.map((b, i) => (
          <div className="bar" key={i}>
            <span className="bar-failed" style={{ height: `${pct(b.failed, peak)}%` }} />
            <span className="bar-ok" style={{ height: `${pct(b.ok, peak)}%` }} />
          </div>
        ))}
      </div>
      <div className="chart-axis">
        <span>{range.label} ago</span>
        <span>now</span>
      </div>
      <div className="legend">
        <span>
          <i />
          Created
        </span>
        <span>
          <i className="failed" />
          Failed or dead
        </span>
      </div>
    </Panel>
  );
}

function StatusBreakdown({ counts, total }) {
  return (
    <Panel title="Jobs by status" note={`${number(total)} total`} className="span-4">
      <ul className="stat-list">
        {JOB_STATUSES.map((status) => (
          <li key={status} className={counts[status] === 0 ? 'zero' : undefined}>
            <span className="stat-name">{statusLabel(status)}</span>
            <span className="stat-count">{number(counts[status])}</span>
            <span className="track">
              <span
                className={`fill ${statusClass(status)}`}
                style={{ width: `${pct(counts[status], total)}%` }}
              />
            </span>
          </li>
        ))}
      </ul>
    </Panel>
  );
}

function RecentJobs({ jobs, onOpen }) {
  const latest = [...jobs].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, LIST_LIMIT);
  return (
    <Panel
      title="Recent jobs"
      note={jobs.length > LIST_LIMIT ? `latest ${LIST_LIMIT} of ${number(jobs.length)}` : undefined}
      action={
        <a className="link-button" href="#/jobs">
          View all
        </a>
      }
      className="span-8"
    >
      {latest.length === 0 ? (
        <p className="empty-line">No jobs yet. Submit one from the Submit job page.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Job</th>
                <th>Task</th>
                <th>Tenant</th>
                <th>Status</th>
                <th className="num">Created</th>
              </tr>
            </thead>
            <tbody>
              {latest.map((job) => (
                <tr key={job.id} className="clickable" onClick={() => onOpen(job.id)}>
                  <td className="mono">{String(job.id).slice(0, 8)}</td>
                  <td>{job.taskType}</td>
                  <td>{job.tenantId}</td>
                  <td>
                    <StatusPill status={job.status} />
                  </td>
                  <td className="num muted">{relTime(job.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}

function Workers({ workers }) {
  const online = workers.filter(isOnline);
  const offline = workers.length - online.length;
  const shown = online.slice(0, LIST_LIMIT);
  const hidden = online.length - shown.length;
  const summary = `${online.length} online${offline ? `, ${offline} offline` : ''}`;
  return (
    <Panel
      title="Workers"
      note={summary}
      action={
        <a className="link-button" href="#/workers">
          View all
        </a>
      }
      className="span-4"
    >
      {shown.length === 0 ? (
        <p className="empty-line">{workers.length === 0 ? 'No workers registered.' : 'No workers online.'}</p>
      ) : (
        <ul className="stat-list">
          {shown.map((w) => {
            const active = Number(w.activeJobs || 0);
            const slots = Number(w.maxConcurrency || 0);
            return (
              <li key={w.workerId}>
                <span className="stat-name mono" title={w.workerId}>
                  {w.hostname || w.workerId}
                </span>
                <span className="stat-count">
                  {number(active)} / {number(slots)}
                </span>
                <span className="track">
                  <span className="fill running" style={{ width: `${pct(active, slots)}%` }} />
                </span>
              </li>
            );
          })}
          {hidden > 0 && <li className="more">+ {hidden} more online</li>}
        </ul>
      )}
    </Panel>
  );
}

export function Overview() {
  const { stats, jobs, workers, setDetailId, config } = useApp();
  const raw = stats.jobs || {};
  const counts = Object.fromEntries(JOB_STATUSES.map((k) => [k, Number(raw[k] || 0)]));
  const total = JOB_STATUSES.reduce((sum, k) => sum + counts[k], 0);
  const failed = counts.FAILED + counts.DEAD;
  const finished = counts.SUCCEEDED + failed;
  const online = workers.filter(isOnline).length;

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Overview</h1>
          <p className="page-subtitle">
            {config?.environment ? `${config.environment} environment, refreshes` : 'Refreshes'} every 10
            seconds
          </p>
        </div>
        <RefreshButton />
      </div>

      <dl className="kpis">
        <Kpi label="Total jobs" value={number(total)} />
        <Kpi label="In flight" value={number(counts.PENDING + counts.RUNNING)} />
        <Kpi label="Success rate" value={finished ? `${pct(counts.SUCCEEDED, finished).toFixed(1)}%` : '—'} />
        <Kpi label="Failed or dead" value={number(failed)} bad={failed > 0} />
        <Kpi label="Workers online" value={number(online)} bad={workers.length > 0 && online === 0} />
        <Kpi label="Scheduler" value={stats.schedulerLeader ? 'Leader' : 'Standby'} />
      </dl>

      <div className="dashboard">
        <Throughput jobs={jobs} />
        <StatusBreakdown counts={counts} total={total} />
        <RecentJobs jobs={jobs} onOpen={setDetailId} />
        <Workers workers={workers} />
      </div>
    </>
  );
}
