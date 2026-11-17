import { useState } from 'react';
import { useApp } from '../context';
import { api } from '../api';

const PAYLOADS = {
  echo: '{\n  "message": "hello from Orbit"\n}',
  sleep: '{\n  "millis": 1200\n}',
  sum: '{\n  "values": [10, 20, 30]\n}',
};

const TASK_HINTS = {
  echo: 'Returns the given message.',
  sleep: 'Waits for the given milliseconds (max 30,000).',
  sum: 'Adds up a list of numbers.',
};

const PRIORITIES = [
  [9, 'Highest'],
  [7, 'High'],
  [5, 'Normal'],
  [3, 'Low'],
  [0, 'Lowest'],
];

function parsePayload(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error('Payload must contain valid JSON.');
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('Payload must be a JSON object.');
  }
  return parsed;
}

export function SubmitJob() {
  const { showToast, refresh, setDetailId, isAdmin } = useApp();
  const [tenant, setTenant] = useState('demo-team');
  const [type, setType] = useState('echo');
  const [priority, setPriority] = useState('5');
  const [attempts, setAttempts] = useState('5');
  const [payload, setPayload] = useState(PAYLOADS.echo);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [payloadError, setPayloadError] = useState('');

  if (!isAdmin) {
    return (
      <>
        <div className="page-heading">
          <h1>Submit job</h1>
        </div>
        <section className="box">
          <div className="blank">
            <h2>Read-only account</h2>
            <p>Your role cannot submit jobs. Ask an administrator for access.</p>
          </div>
        </section>
      </>
    );
  }

  const onTypeChange = (e) => {
    setType(e.target.value);
    setPayload(PAYLOADS[e.target.value]);
    setPayloadError('');
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    let parsed;
    try {
      parsed = parsePayload(payload);
      setPayloadError('');
    } catch (err) {
      setPayloadError(err.message);
      return;
    }
    setBusy(true);
    setResult(null);
    try {
      const job = await api.submit({
        tenantId: tenant.trim(),
        taskType: type,
        payload: parsed,
        priority: Number(priority),
        maxAttempts: Number(attempts),
      });
      setResult(job);
      showToast('Job accepted.', 'success');
      refresh();
    } catch (err) {
      showToast(err.message || 'Unable to submit job.', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Submit job</h1>
          <p className="page-subtitle">Queue a task for the next available worker.</p>
        </div>
      </div>

      <form className="compose" onSubmit={onSubmit}>
        <div className="compose-main">
          <div className="field">
            <label htmlFor="tenant">Tenant ID</label>
            <input
              id="tenant"
              value={tenant}
              onChange={(e) => setTenant(e.target.value)}
              maxLength={120}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="payload">Payload</label>
            <textarea
              id="payload"
              spellCheck={false}
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              aria-invalid={Boolean(payloadError)}
              aria-describedby="payload-help"
              required
            />
            <p id="payload-help" className={`field-hint${payloadError ? ' error' : ''}`}>
              {payloadError || 'A JSON object passed to the task handler.'}
            </p>
          </div>
          <div className="compose-actions">
            {result && (
              <span className="accepted">
                Accepted{' '}
                <button className="link-button mono" type="button" onClick={() => setDetailId(result.id)}>
                  #{String(result.id).slice(0, 8)}
                </button>
                <a className="link-button" href="#/jobs">
                  View jobs
                </a>
              </span>
            )}
            <button className="button primary" type="submit" disabled={busy}>
              {busy ? 'Submitting…' : 'Submit job'}
            </button>
          </div>
        </div>

        <aside className="compose-side" aria-label="Job options">
          <div className="side-block">
            <label htmlFor="type">Task type</label>
            <select id="type" value={type} onChange={onTypeChange}>
              {Object.keys(PAYLOADS).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            <p className="field-hint">{TASK_HINTS[type]}</p>
          </div>
          <div className="side-block">
            <label htmlFor="priority">Priority</label>
            <select id="priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
              {PRIORITIES.map(([v, name]) => (
                <option key={v} value={v}>
                  {name} ({v})
                </option>
              ))}
            </select>
            <p className="field-hint">Higher priority jobs are dispatched first.</p>
          </div>
          <div className="side-block">
            <label htmlFor="attempts">Max attempts</label>
            <input
              id="attempts"
              type="number"
              min="1"
              max="20"
              value={attempts}
              onChange={(e) => setAttempts(e.target.value)}
              required
            />
            <p className="field-hint">Failed jobs are retried up to this many times.</p>
          </div>
        </aside>
      </form>
    </>
  );
}
