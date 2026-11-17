import { useCallback, useEffect, useState } from 'react';
import { useApp } from '../context';
import { api } from '../api';
import { RefreshButton } from '../components';
import { relTime } from '../utils';

const MIN_PASSWORD = 12;

function AddUser({ onCreated, onCancel }) {
  const { showToast } = useApp();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('VIEWER');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    if (password.length < MIN_PASSWORD) {
      setError(`Password must be at least ${MIN_PASSWORD} characters.`);
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api.createUser({ username: username.trim(), password, role });
      showToast(`User ${username.trim()} created.`, 'success');
      onCreated();
    } catch (err) {
      setError(err.message || 'Unable to create the user.');
      setBusy(false);
    }
  };

  return (
    <form className="box add-user" onSubmit={submit}>
      <div className="add-user-fields">
        <div className="field">
          <label htmlFor="new-username">Username</label>
          <input
            id="new-username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="off"
            spellCheck={false}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="new-password">Password</label>
          <input
            id="new-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
        </div>
        <div className="field">
          <label htmlFor="new-role">Role</label>
          <select id="new-role" value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="VIEWER">Viewer (read-only)</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </div>
      </div>
      {error && (
        <p className="field-hint error" role="alert">
          {error}
        </p>
      )}
      <div className="add-user-actions">
        <button className="button" type="button" onClick={onCancel}>
          Cancel
        </button>
        <button className="button primary" type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create user'}
        </button>
      </div>
    </form>
  );
}

export function Users() {
  const { user: me, showToast } = useApp();
  const [users, setUsers] = useState(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    try {
      setUsers(await api.users());
    } catch (e) {
      showToast(e.message || 'Unable to load users.', 'error');
    }
  }, [showToast]);

  useEffect(() => {
    load();
  }, [load]);

  const change = async (target, patch) => {
    try {
      await api.updateUser(target.id, patch);
      await load();
    } catch (e) {
      showToast(e.message || 'Unable to update the user.', 'error');
    }
  };

  const admins = (users || []).filter((u) => u.role === 'ADMIN' && u.enabled).length;

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Users</h1>
          <p className="page-subtitle">
            {users
              ? `${users.length} ${users.length === 1 ? 'user' : 'users'}, ${admins} active ${admins === 1 ? 'administrator' : 'administrators'}`
              : 'Loading…'}
          </p>
        </div>
        <div className="heading-actions">
          <RefreshButton onClick={load} />
          {!adding && (
            <button className="button primary" type="button" onClick={() => setAdding(true)}>
              Add user
            </button>
          )}
        </div>
      </div>

      {adding && (
        <AddUser
          onCancel={() => setAdding(false)}
          onCreated={() => {
            setAdding(false);
            load();
          }}
        />
      )}

      <section className="box">
        {!users ? (
          <div className="blank">
            <p>Loading users…</p>
          </div>
        ) : (
          <ul className="rows">
            {users.map((u) => {
              const self = me && u.username.toLowerCase() === me.username.toLowerCase();
              return (
                <li className={`row${u.enabled ? '' : ' dimmed'}`} key={u.id}>
                  <div className="row-main static">
                    <span className="row-title">
                      {u.username}
                      {self && <span className="label accent">You</span>}
                      {!u.enabled && <span className="label">Disabled</span>}
                      {u.locked && <span className="label">Locked</span>}
                    </span>
                    <span className="row-sub">
                      <span>
                        {u.lastLoginAt ? `Last sign-in ${relTime(u.lastLoginAt)}` : 'Never signed in'}
                      </span>
                      <span>Created {relTime(u.createdAt)}</span>
                    </span>
                  </div>
                  <label className="row-role">
                    <span className="sr-only">Role for {u.username}</span>
                    <select
                      value={u.role}
                      disabled={self}
                      onChange={(e) => change(u, { role: e.target.value })}
                    >
                      <option value="ADMIN">Administrator</option>
                      <option value="VIEWER">Viewer</option>
                    </select>
                  </label>
                  <span className="row-action wide">
                    {!self && (
                      <button
                        className={`link-button${u.enabled ? ' danger' : ''}`}
                        type="button"
                        onClick={() => change(u, { enabled: !u.enabled })}
                      >
                        {u.enabled ? 'Disable' : 'Enable'}
                      </button>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </>
  );
}
