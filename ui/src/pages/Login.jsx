import { useState } from 'react';
import { useApp } from '../context';

export function Login({ note }) {
  const { signIn } = useApp();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    const message = await signIn(username, password);
    if (message) {
      setError(message);
      setPassword('');
      setBusy(false);
    }
  };

  const message = error || note;

  return (
    <main className="login">
      <div className="login-stack">
        <section className="login-panel" aria-labelledby="signin-title">
          <picture>
            <source media="(prefers-color-scheme: dark)" srcSet="/logo-dark.svg" />
            <img className="logo" src="/logo.svg" alt="Orbit" height="56" />
          </picture>
          <h1 id="signin-title" className="sr-only">
            Sign in
          </h1>

          {message && (
            <p className={`login-message${error ? ' error' : ''}`} role={error ? 'alert' : 'status'}>
              {message}
            </p>
          )}

          <form onSubmit={onSubmit} noValidate>
            <div className="field">
              <label htmlFor="username">Username</label>
              <input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                autoFocus
                spellCheck={false}
                autoCapitalize="none"
              />
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <div className="token-field">
                <input
                  id="password"
                  type={show ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                />
                <button
                  className="text-button"
                  type="button"
                  onClick={() => setShow((s) => !s)}
                  aria-pressed={show}
                >
                  {show ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>
            <button className="button primary block" type="submit" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}
