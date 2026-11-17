import { useState } from 'react';
import { useApp } from '../context';
import { RefreshButton } from '../components';
import { number, relTime } from '../utils';

const isOnline = (w) => String(w.status).toUpperCase() === 'ONLINE';
const FILTERS = [
  { key: 'ALL', label: 'All', match: () => true },
  { key: 'ONLINE', label: 'Online', match: isOnline },
  { key: 'OFFLINE', label: 'Offline', match: (w) => !isOnline(w) },
];

function WorkerRow({ worker: w }) {
  const online = isOnline(w);
  const max = Number(w.maxConcurrency || 0);
  const active = Number(w.activeJobs || 0);
  const load = max > 0 ? Math.min(100, (active / max) * 100) : 0;
  return (
    <li className={`row${online ? '' : ' dimmed'}`}>
      <div className="row-main static">
        <span className="row-title">
          {w.hostname || w.workerId}
          <span className={`label ${online ? 'success' : ''}`}>{online ? 'Online' : 'Offline'}</span>
        </span>
        <span className="row-sub">
          <span className="mono" title={w.workerId}>
            {w.workerId}
          </span>
          <span>registered {relTime(w.registeredAt)}</span>
        </span>
      </div>
      <div className="row-load" aria-label={`${active} of ${max} slots in use`}>
        <div className="load-text">
          <span>{online ? `${number(active)} / ${number(max)}` : `0 / ${number(max)}`}</span>
          <span className="muted">slots</span>
        </div>
        <div className="meter">
          <span style={{ width: `${online ? load : 0}%` }} />
        </div>
      </div>
      <div className="row-stat">
        <strong>{number(w.completedJobs)}</strong>
        <span className="muted">completed</span>
      </div>
      <div className="row-stat">
        <strong>{relTime(w.lastHeartbeat)}</strong>
        <span className="muted">heartbeat</span>
      </div>
    </li>
  );
}

export function Workers() {
  const { workers } = useApp();
  const [choice, setFilter] = useState(null);
  const filter = choice ?? (workers.some(isOnline) ? 'ONLINE' : 'ALL');
  const online = workers.filter(isOnline).length;
  const slots = workers.filter(isOnline).reduce((s, w) => s + Number(w.maxConcurrency || 0), 0);
  const busy = workers.filter(isOnline).reduce((s, w) => s + Number(w.activeJobs || 0), 0);
  const active = FILTERS.find((f) => f.key === filter);
  const visible = workers.filter(active.match);

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Workers</h1>
          <p className="page-subtitle">
            {online} of {workers.length} online, {number(busy)} of {number(slots)} slots in use
          </p>
        </div>
        <RefreshButton />
      </div>

      <section className="box">
        <header className="box-header">
          <div className="segments" role="group" aria-label="Filter by status">
            {FILTERS.map((f) => (
              <button
                key={f.key}
                type="button"
                className={filter === f.key ? 'active' : undefined}
                aria-pressed={filter === f.key}
                onClick={() => setFilter(f.key)}
              >
                {f.label}
                <span>{workers.filter(f.match).length}</span>
              </button>
            ))}
          </div>
        </header>
        {visible.length === 0 ? (
          <div className="blank">
            <h2>{workers.length === 0 ? 'No workers registered' : 'No workers match this filter'}</h2>
            <p>
              {workers.length === 0
                ? 'Workers register themselves with the scheduler over gRPC when they start.'
                : 'Try another filter.'}
            </p>
          </div>
        ) : (
          <ul className="rows">
            {visible.map((w) => (
              <WorkerRow key={w.workerId} worker={w} />
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
