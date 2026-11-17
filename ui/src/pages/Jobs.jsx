import { useMemo, useState } from 'react';
import { useApp } from '../context';
import { RefreshButton, StatusIcon } from '../components';
import { api } from '../api';
import { JOB_GROUPS, duration, number, relTime } from '../utils';

const PAGE_SIZE = 20;
const upper = (v) => String(v || '').toUpperCase();
const ACTIVE = ['PENDING', 'RUNNING'];

const SORTS = {
  newest: { label: 'Newest', cmp: (a, b) => new Date(b.createdAt) - new Date(a.createdAt) },
  oldest: { label: 'Oldest', cmp: (a, b) => new Date(a.createdAt) - new Date(b.createdAt) },
  priority: {
    label: 'Highest priority',
    cmp: (a, b) => b.priority - a.priority || new Date(b.createdAt) - new Date(a.createdAt),
  },
  attempts: { label: 'Most attempts', cmp: (a, b) => b.attempts - a.attempts },
};

function CancelButton({ id }) {
  const { refresh, showToast } = useApp();
  const [busy, setBusy] = useState(false);
  const onClick = async () => {
    setBusy(true);
    try {
      await api.cancel(id);
      showToast('Job cancelled.', 'success');
      await refresh();
    } catch (err) {
      showToast(err.message || 'Unable to cancel job.', 'error');
    } finally {
      setBusy(false);
    }
  };
  return (
    <button className="link-button danger" type="button" onClick={onClick} disabled={busy}>
      Cancel
    </button>
  );
}

function JobRow({ job, onOpen, canCancel }) {
  const status = upper(job.status);
  const finished = job.finishedAt && job.startedAt;
  return (
    <li className="row">
      <StatusIcon status={job.status} />
      <button className="row-main" type="button" onClick={() => onOpen(job.id)}>
        <span className="row-title">
          {job.taskType}
          <span className="label">{job.tenantId}</span>
          {job.priority >= 7 && <span className="label accent">P{job.priority}</span>}
        </span>
        <span className="row-sub">
          <span className="mono">#{String(job.id).slice(0, 8)}</span>
          <span>Created {relTime(job.createdAt)}</span>
          {job.attempts > 1 && (
            <span>
              Attempt {job.attempts} of {job.maxAttempts}
            </span>
          )}
          {job.lastError && status !== 'SUCCEEDED' && <span>Has error</span>}
        </span>
      </button>
      <span className="row-meta">
        {finished ? duration(job.startedAt, job.finishedAt) : status === 'RUNNING' ? 'running' : ''}
      </span>
      <span className="row-action">
        {canCancel && ACTIVE.includes(status) && <CancelButton id={job.id} />}
      </span>
    </li>
  );
}

export function Jobs() {
  const { jobs, setDetailId, isAdmin } = useApp();
  const [group, setGroup] = useState('ALL');
  const [query, setQuery] = useState('');
  const [tenant, setTenant] = useState('');
  const [task, setTask] = useState('');
  const [sort, setSort] = useState('newest');
  const [page, setPage] = useState(0);

  const tenants = useMemo(() => [...new Set(jobs.map((j) => j.tenantId))].sort(), [jobs]);
  const tasks = useMemo(() => [...new Set(jobs.map((j) => j.taskType))].sort(), [jobs]);

  const q = query.trim().toLowerCase();
  const base = useMemo(
    () =>
      jobs.filter((j) => {
        if (tenant && j.tenantId !== tenant) return false;
        if (task && j.taskType !== task) return false;
        return (
          !q ||
          [j.tenantId, j.taskType, j.id].some((f) =>
            String(f || '')
              .toLowerCase()
              .includes(q),
          )
        );
      }),
    [jobs, tenant, task, q],
  );

  const countOf = (g) => base.filter((j) => g.match(upper(j.status))).length;
  const active = JOB_GROUPS.find((g) => g.key === group);
  const visible = useMemo(
    () => base.filter((j) => active.match(upper(j.status))).sort(SORTS[sort].cmp),
    [base, active, sort],
  );

  const pages = Math.max(1, Math.ceil(visible.length / PAGE_SIZE));
  const current = Math.min(page, pages - 1);
  const slice = visible.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE);
  const filtered = Boolean(q || tenant || task);
  const reset = (fn) => (e) => {
    fn(e.target.value);
    setPage(0);
  };

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Jobs</h1>
          <p className="page-subtitle">
            {number(jobs.length)} jobs across {tenants.length} {tenants.length === 1 ? 'tenant' : 'tenants'}
          </p>
        </div>
        <RefreshButton />
      </div>

      <div className="searchbar">
        <label className="search">
          <input
            type="search"
            value={query}
            onChange={reset(setQuery)}
            placeholder="Search by task, tenant or job ID"
            aria-label="Search jobs"
          />
        </label>
        {isAdmin && (
          <a className="button primary" href="#/submit">
            Submit job
          </a>
        )}
      </div>

      <section className="box">
        <header className="box-header">
          <div className="segments" role="group" aria-label="Filter by state">
            {JOB_GROUPS.map((g) => (
              <button
                key={g.key}
                type="button"
                className={group === g.key ? 'active' : undefined}
                aria-pressed={group === g.key}
                onClick={() => {
                  setGroup(g.key);
                  setPage(0);
                }}
              >
                {g.label}
                <span>{number(countOf(g))}</span>
              </button>
            ))}
          </div>
          <div className="filters">
            <label className="filter">
              <span className="sr-only">Tenant</span>
              <select value={tenant} onChange={reset(setTenant)}>
                <option value="">All tenants</option>
                {tenants.map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </label>
            <label className="filter">
              <span className="sr-only">Task type</span>
              <select value={task} onChange={reset(setTask)}>
                <option value="">All tasks</option>
                {tasks.map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </label>
            <label className="filter">
              <span className="sr-only">Sort</span>
              <select value={sort} onChange={reset(setSort)}>
                {Object.entries(SORTS).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </header>

        {slice.length === 0 ? (
          <div className="blank">
            <h2>{filtered || group !== 'ALL' ? 'No jobs match your filters' : 'No jobs yet'}</h2>
            <p>
              {filtered || group !== 'ALL'
                ? 'Try a different search or clear the filters.'
                : 'Submit a job and it will appear here.'}
            </p>
            {(filtered || group !== 'ALL') && (
              <button
                className="button"
                type="button"
                onClick={() => {
                  setQuery('');
                  setTenant('');
                  setTask('');
                  setGroup('ALL');
                  setPage(0);
                }}
              >
                Clear filters
              </button>
            )}
          </div>
        ) : (
          <ul className="rows">
            {slice.map((job) => (
              <JobRow key={job.id} job={job} onOpen={setDetailId} canCancel={isAdmin} />
            ))}
          </ul>
        )}

        {visible.length > PAGE_SIZE && (
          <footer className="box-footer">
            <span className="muted">
              {current * PAGE_SIZE + 1}–{Math.min((current + 1) * PAGE_SIZE, visible.length)} of{' '}
              {number(visible.length)}
            </span>
            <div className="pager">
              <button
                className="button"
                type="button"
                disabled={current === 0}
                onClick={() => setPage(current - 1)}
              >
                Previous
              </button>
              <span className="muted">
                Page {current + 1} of {pages}
              </span>
              <button
                className="button"
                type="button"
                disabled={current >= pages - 1}
                onClick={() => setPage(current + 1)}
              >
                Next
              </button>
            </div>
          </footer>
        )}
      </section>
    </>
  );
}
