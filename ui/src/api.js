const TOKEN_KEY = 'orbit-admin-api-token';

const USER_KEY = 'orbit-user';

export const getToken = () => sessionStorage.getItem(TOKEN_KEY) || '';
export const setToken = (token) =>
  token ? sessionStorage.setItem(TOKEN_KEY, token) : sessionStorage.removeItem(TOKEN_KEY);

export const getStoredUser = () => {
  try {
    return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null');
  } catch {
    return null;
  }
};
export const setStoredUser = (user) =>
  user ? sessionStorage.setItem(USER_KEY, JSON.stringify(user)) : sessionStorage.removeItem(USER_KEY);

// crypto.randomUUID only exists on HTTPS or localhost.
const newKey = () =>
  crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export const isAuthError = (e) => e instanceof ApiError && (e.status === 401 || e.status === 403);

async function ensureOk(res) {
  if (!res.ok) throw new ApiError(await responseMessage(res), res.status);
  return res.json();
}

function authFetch(url, options = {}) {
  const token = getToken();
  const headers = new Headers(options.headers || {});
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(url, { ...options, headers });
}

async function responseMessage(response) {
  const ct = response.headers.get('content-type') || '';
  if (ct.includes('application/json')) {
    const body = await response.json().catch(() => ({}));
    return body.detail || body.message || body.error || `Request failed (${response.status})`;
  }
  return (await response.text().catch(() => '')) || `Request failed (${response.status})`;
}

const json = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export const api = {
  login: (username, password) =>
    fetch('/api/v1/auth/login', json('POST', { username, password })).then(ensureOk),
  logout: () => authFetch('/api/v1/auth/logout', { method: 'POST' }).catch(() => undefined),
  me: () => authFetch('/api/v1/auth/me').then(ensureOk),
  users: () => authFetch('/api/v1/users').then(ensureOk),
  createUser: (body) => authFetch('/api/v1/users', json('POST', body)).then(ensureOk),
  updateUser: (id, body) =>
    authFetch(`/api/v1/users/${encodeURIComponent(id)}`, json('PATCH', body)).then(ensureOk),
  stats: () => authFetch('/api/v1/stats').then(ensureOk),
  jobs: (params = {}) => {
    const q = new URLSearchParams();
    if (params.status) q.set('status', params.status);
    if (params.tenantId) q.set('tenantId', params.tenantId);
    const qs = q.toString();
    return authFetch('/api/v1/jobs' + (qs ? `?${qs}` : '')).then(ensureOk);
  },
  workers: () => authFetch('/api/v1/workers').then(ensureOk),
  job: (id) => authFetch(`/api/v1/jobs/${encodeURIComponent(id)}`).then(ensureOk),
  config: () => authFetch('/api/v1/config').then(ensureOk),
  submit: (body) =>
    authFetch('/api/v1/jobs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': newKey() },
      body: JSON.stringify(body),
    }).then(ensureOk),
  cancel: (id) => authFetch(`/api/v1/jobs/${encodeURIComponent(id)}`, { method: 'DELETE' }).then(ensureOk),
};
