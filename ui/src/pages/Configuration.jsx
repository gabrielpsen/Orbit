import { useApp } from '../context';
import { RefreshButton } from '../components';
import { isHttpUrl } from '../utils';

const orDash = (v, suffix = '') => (v === undefined || v === null ? '—' : `${v}${suffix}`);

const SECTIONS = [
  { id: 'runtime', label: 'Runtime' },
  { id: 'scheduler', label: 'Scheduler' },
  { id: 'worker', label: 'Worker' },
  { id: 'monitoring', label: 'Monitoring' },
];

function Setting({ label, hint, children }) {
  return (
    <div className="setting">
      <div className="setting-label">
        <span>{label}</span>
        {hint && <small>{hint}</small>}
      </div>
      <div className="setting-value">{children}</div>
    </div>
  );
}

function Group({ id, title, description, children }) {
  return (
    <section className="group" id={`cfg-${id}`}>
      <header className="group-header">
        <h2>{title}</h2>
        <p>{description}</p>
      </header>
      <div className="box">{children}</div>
    </section>
  );
}

function Link({ href }) {
  return isHttpUrl(href) ? (
    <a className="link-button" href={href} target="_blank" rel="noreferrer">
      {href}
    </a>
  ) : (
    '—'
  );
}

export function Configuration() {
  const { config } = useApp();
  const grpc = config?.grpc || {};
  const scheduler = config?.scheduler || {};
  const worker = config?.worker || {};
  const endpoints = config?.endpoints || {};

  const jump = (id) =>
    document.getElementById(`cfg-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Configuration</h1>
          <p className="page-subtitle">Read-only view of the running scheduler configuration.</p>
        </div>
        <RefreshButton />
      </div>

      <div className="settings">
        <nav className="settings-nav" aria-label="Configuration sections">
          {SECTIONS.map((s) => (
            <button key={s.id} type="button" onClick={() => jump(s.id)}>
              {s.label}
            </button>
          ))}
        </nav>

        <div className="settings-body">
          {config ? (
            <>
              <Group id="runtime" title="Runtime" description="Deployment environment and transport.">
                <Setting label="Environment">{orDash(config.environment)}</Setting>
                <Setting label="Role">{orDash(config.role)}</Setting>
                <Setting label="Mutual TLS" hint="Required in production">
                  {config.tlsEnabled ? 'Enabled' : 'Disabled'}
                </Setting>
                <Setting label="gRPC endpoint">
                  <span className="mono">
                    {orDash(grpc.host)}:{orDash(grpc.port)}
                  </span>
                </Setting>
                <Setting label="Task types" hint="Registered handlers">
                  {(config.taskTypes || []).map((t) => (
                    <span className="label" key={t}>
                      {t}
                    </span>
                  ))}
                </Setting>
              </Group>

              <Group id="scheduler" title="Scheduler" description="Dispatch and recovery timing.">
                <Setting label="Dispatch interval">{orDash(scheduler.dispatchIntervalMs, ' ms')}</Setting>
                <Setting label="Lease duration">{orDash(scheduler.leaseSeconds, ' s')}</Setting>
                <Setting label="Heartbeat timeout">{orDash(scheduler.heartbeatTimeoutSeconds, ' s')}</Setting>
                <Setting label="Recovery interval">{orDash(scheduler.recoveryIntervalMs, ' ms')}</Setting>
                <Setting label="Default tenant concurrency">
                  {orDash(scheduler.defaultTenantConcurrency)}
                </Setting>
              </Group>

              <Group id="worker" title="Worker" description="Execution defaults.">
                <Setting label="Concurrency">{orDash(worker.concurrency)}</Setting>
                <Setting label="Poll interval">{orDash(worker.pollIntervalMs, ' ms')}</Setting>
                <Setting label="Heartbeat interval">{orDash(worker.heartbeatIntervalMs, ' ms')}</Setting>
                <Setting label="Max runtime">{orDash(worker.maxRuntimeSeconds, ' s')}</Setting>
              </Group>

              <Group id="monitoring" title="Monitoring" description="Observability endpoints.">
                <Setting label="Prometheus">
                  <Link href={endpoints.prometheus} />
                </Setting>
                <Setting label="Grafana">
                  <Link href={endpoints.grafana} />
                </Setting>
              </Group>
            </>
          ) : (
            <section className="box">
              <div className="blank">
                <h2>Loading configuration</h2>
                <p>The runtime configuration will appear here in a moment.</p>
              </div>
            </section>
          )}
        </div>
      </div>
    </>
  );
}
