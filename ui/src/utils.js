export const number = (v) => Number(v || 0).toLocaleString();

export const isHttpUrl = (v) => /^https?:\/\//i.test(v || '');

export const relTime = (v) => {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return '—';
  const sec = Math.max(0, Math.round((Date.now() - d.getTime()) / 1000));
  if (sec < 60) return `${sec}s ago`;
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  return `${Math.round(hr / 24)}d ago`;
};

export const prettyJson = (v) => {
  if (!v) return '—';
  try {
    return JSON.stringify(JSON.parse(v), null, 2);
  } catch {
    return String(v);
  }
};

export const JOB_STATUSES = ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DEAD', 'CANCELLED'];

export const statusLabel = (status) => {
  const s = String(status || 'unknown').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
};

export const statusClass = (status) => {
  const s = String(status || '').toLowerCase();
  return ['pending', 'running', 'succeeded', 'failed', 'dead', 'cancelled'].includes(s) ? s : 'neutral';
};

export const duration = (start, end) => {
  if (!start || !end) return '—';
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (Number.isNaN(ms) || ms < 0) return '—';
  if (ms < 1000) return `${ms} ms`;
  const sec = ms / 1000;
  if (sec < 60) return `${sec.toFixed(1)} s`;
  const min = Math.floor(sec / 60);
  return `${min}m ${Math.round(sec % 60)}s`;
};

export const JOB_GROUPS = [
  { key: 'ALL', label: 'All', match: () => true },
  { key: 'ACTIVE', label: 'Active', match: (s) => ['PENDING', 'RUNNING'].includes(s) },
  { key: 'SUCCEEDED', label: 'Succeeded', match: (s) => s === 'SUCCEEDED' },
  { key: 'FAILED', label: 'Failed', match: (s) => ['FAILED', 'DEAD'].includes(s) },
  { key: 'CANCELLED', label: 'Cancelled', match: (s) => s === 'CANCELLED' },
];
