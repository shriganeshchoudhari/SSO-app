import React, { useEffect, useState } from 'react'

// ── Runtime configuration ─────────────────────────────────────────────────────
// Nginx injects window.__OI_CONFIG__ via a /config.js endpoint so the UI
// never requires users to type realm or clientId. Fallback to URL params for
// local development (e.g. ?realm=demo&clientId=web-app).
declare global {
  interface Window {
    __OI_CONFIG__?: { realm: string; clientId: string }
  }
}

function resolveConfig(): { realm: string; clientId: string } {
  if (window.__OI_CONFIG__?.realm && window.__OI_CONFIG__?.clientId) {
    return window.__OI_CONFIG__
  }
  const params = new URLSearchParams(window.location.search)
  const realm    = params.get('realm')    ?? import.meta.env.VITE_REALM    ?? 'demo'
  const clientId = params.get('clientId') ?? import.meta.env.VITE_CLIENT_ID ?? 'account'
  return { realm, clientId }
}

type User = {
  id: string
  realmId: string
  username: string
  email?: string
  enabled?: boolean
  emailVerified?: boolean
  federationSource?: string | null
}
type Session = { id: string; started: string; lastRefresh: string }
type TokenResponse = {
  access_token: string
  refresh_token?: string
  id_token?: string
  expires_in: number
}

export default function App() {
  const config = resolveConfig()
  const realm    = config.realm
  const clientId = config.clientId

  const [username, setUsername]       = useState('')
  const [password, setPassword]       = useState('')
  const [totpCode, setTotpCode]       = useState('')
  const [accessToken, setAccessToken] = useState('')
  const [refreshToken, setRefreshToken] = useState('')
  const [user, setUser]               = useState<User | null>(null)
  const [email, setEmail]             = useState('')
  const [sessions, setSessions]       = useState<Session[]>([])
  const [newPassword, setNewPassword] = useState('')
  const [totpSecret, setTotpSecret]   = useState<string | null>(null)
  const [totpUri, setTotpUri]         = useState<string | null>(null)
  const [isWorking, setIsWorking]     = useState(false)
  const [message, setMessage]         = useState<string | null>(null)
  const [error, setError]             = useState<string | null>(null)

  const canUseAccount      = accessToken.trim().length > 0
  const externallyManaged  = Boolean(user?.federationSource)

  const authHeaders = (json = false) => ({
    Authorization: `Bearer ${accessToken.trim()}`,
    ...(json ? { 'Content-Type': 'application/json' } : {})
  })
  const setFeedback = (msg: string | null, err: string | null) => {
    setMessage(msg); setError(err)
  }

  // ── Data loading ─────────────────────────────────────────────────────────────

  const loadUser = async () => {
    if (!canUseAccount) return
    const res = await fetch('/account/profile', { headers: authHeaders() })
    if (!res.ok) { setUser(null); setEmail(''); setFeedback(null, 'Failed to load profile.'); return }
    const data = await res.json()
    setUser(data); setEmail(data.email ?? ''); setFeedback(null, null)
  }

  const loadSessions = async () => {
    if (!canUseAccount) return
    const res = await fetch('/account/sessions', { headers: authHeaders() })
    if (!res.ok) { setSessions([]); setFeedback(null, 'Failed to load sessions.'); return }
    setSessions(await res.json())
  }

  useEffect(() => {
    if (!accessToken.trim()) {
      setUser(null); setSessions([]); setTotpSecret(null); setTotpUri(null); return
    }
    loadUser(); loadSessions()
  }, [accessToken])

  // ── Auth actions ──────────────────────────────────────────────────────────────

  const login = async (e: React.FormEvent) => {
    e.preventDefault(); setIsWorking(true); setFeedback(null, null)
    try {
      const body = new URLSearchParams()
      body.set('grant_type', 'password')
      body.set('client_id', clientId)
      body.set('username', username)
      body.set('password', password)
      if (totpCode.trim()) body.set('totp', totpCode.trim())
      const res = await fetch(
        `/auth/realms/${encodeURIComponent(realm)}/protocol/openid-connect/token`,
        { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body }
      )
      if (!res.ok) {
        setAccessToken(''); setRefreshToken(''); setUser(null); setSessions([])
        setFeedback(null, 'Sign-in failed. Check credentials or TOTP code.')
        return
      }
      const data: TokenResponse = await res.json()
      setAccessToken(data.access_token); setRefreshToken(data.refresh_token ?? '')
      setPassword(''); setTotpCode(''); setFeedback('Signed in successfully.', null)
    } finally { setIsWorking(false) }
  }

  const refreshSession = async () => {
    if (!refreshToken.trim()) { setFeedback(null, 'No refresh token available.'); return }
    setIsWorking(true); setFeedback(null, null)
    try {
      const body = new URLSearchParams()
      body.set('grant_type', 'refresh_token')
      body.set('client_id', clientId)
      body.set('refresh_token', refreshToken)
      const res = await fetch(
        `/auth/realms/${encodeURIComponent(realm)}/protocol/openid-connect/token`,
        { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body }
      )
      if (!res.ok) { setFeedback(null, 'Refresh failed. Sign in again.'); return }
      const data: TokenResponse = await res.json()
      setAccessToken(data.access_token); setRefreshToken(data.refresh_token ?? '')
      setFeedback('Session refreshed.', null)
    } finally { setIsWorking(false) }
  }

  const logoutLocal = () => {
    setAccessToken(''); setRefreshToken(''); setUser(null); setSessions([])
    setTotpSecret(null); setTotpUri(null); setFeedback('Signed out.', null)
  }

  // ── Account actions ───────────────────────────────────────────────────────────

  const saveEmail = async () => {
    const res = await fetch('/account/profile',
      { method: 'PUT', headers: authHeaders(true), body: JSON.stringify({ email }) })
    if (!res.ok) { setFeedback(null, 'Failed to update email.'); return }
    await loadUser(); setFeedback('Profile updated.', null)
  }

  const updatePassword = async () => {
    if (!newPassword) return
    const res = await fetch('/account/credentials/password',
      { method: 'POST', headers: authHeaders(true), body: JSON.stringify({ password: newPassword }) })
    if (!res.ok) { setFeedback(null, 'Failed to update password.'); return }
    setNewPassword(''); setFeedback('Password updated.', null)
  }

  const deleteSession = async (sid: string) => {
    if (!window.confirm('Delete this session?')) return
    const res = await fetch(`/account/sessions/${sid}`,
      { method: 'DELETE', headers: authHeaders() })
    if (!res.ok) { setFeedback(null, 'Failed to delete session.'); return }
    await loadSessions(); setFeedback('Session deleted.', null)
  }

  const enrollTotp = async () => {
    setIsWorking(true); setFeedback(null, null)
    try {
      const res = await fetch('/account/credentials/totp',
        { method: 'POST', headers: authHeaders() })
      if (!res.ok) { setFeedback(null, 'Failed to generate TOTP secret.'); return }
      const data = await res.json()
      setTotpSecret(data.secret); setTotpUri(data.provisioningUri)
      setFeedback('TOTP secret generated.', null)
    } finally { setIsWorking(false) }
  }

  // ── Render ────────────────────────────────────────────────────────────────────

  const s: Record<string, React.CSSProperties> = {
    page:    { padding: 24, fontFamily: 'system-ui, sans-serif', maxWidth: 960, margin: '0 auto' },
    section: { marginBottom: 24, padding: 16, border: '1px solid #ddd', borderRadius: 8 },
    h2:      { marginTop: 0 },
    grid:    { display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(220px,1fr))' },
    row:     { display: 'flex', gap: 8, marginTop: 12 },
    ok:      { color: '#0f5132', marginBottom: 0 },
    err:     { color: '#842029', marginBottom: 0 },
    badge:   { display: 'inline-block', fontSize: 12, padding: '2px 8px', borderRadius: 4,
               background: '#e9ecef', color: '#495057', marginLeft: 6 },
  }

  return (
    <div style={s.page}>
      <h1 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        OpenIdentity Account
        <span style={s.badge}>{realm}</span>
      </h1>

      {/* Sign In */}
      <section style={s.section}>
        <h2 style={s.h2}>Sign in</h2>
        <form onSubmit={login}>
          <div style={s.grid}>
            <label>Username<input value={username} onChange={e => setUsername(e.target.value)} style={{ width: '100%' }} /></label>
            <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} style={{ width: '100%' }} /></label>
            <label>Authenticator code <em style={{ fontSize: 12, color: '#6c757d' }}>(if enrolled)</em>
              <input value={totpCode} onChange={e => setTotpCode(e.target.value)} inputMode="numeric" maxLength={6} style={{ width: '100%' }} />
            </label>
          </div>
          <div style={s.row}>
            <button disabled={isWorking || !username || !password}>Sign in</button>
            <button type="button" disabled={!refreshToken || isWorking} onClick={refreshSession}>Refresh session</button>
            <button type="button" disabled={!accessToken} onClick={logoutLocal}>Sign out</button>
          </div>
        </form>
        {message && <p style={s.ok}>{message}</p>}
        {error   && <p style={s.err}>{error}</p>}
      </section>

      {user ? (
        <>
          {/* Profile */}
          <section style={s.section}>
            <h2 style={s.h2}>Profile</h2>
            <div><strong>Username:</strong> {user.username}</div>
            <div><strong>Identity source:</strong> {user.federationSource ? user.federationSource.toUpperCase() : 'LOCAL'}</div>
            <div style={{ marginTop: 8 }}>
              <strong>Email:</strong>{' '}
              <input value={email} onChange={e => setEmail(e.target.value)} disabled={externallyManaged} />
              <button onClick={saveEmail} style={{ marginLeft: 8 }} disabled={externallyManaged}>Save</button>
            </div>
            {externallyManaged && <p style={{ margin: '8px 0 0', color: '#6c757d' }}>Profile is managed by your external identity provider.</p>}
            <div><strong>Email verified:</strong> {user.emailVerified ? 'Yes' : 'No'}</div>
          </section>

          {/* Change password */}
          <section style={s.section}>
            <h2 style={s.h2}>Change password</h2>
            {externallyManaged
              ? <p style={{ margin: 0, color: '#6c757d' }}>Password is managed by your external identity provider.</p>
              : <><input type="password" placeholder="New password" value={newPassword}
                         onChange={e => setNewPassword(e.target.value)} />
                 <button disabled={!newPassword} onClick={updatePassword} style={{ marginLeft: 8 }}>Update</button></>}
          </section>

          {/* TOTP */}
          <section style={s.section}>
            <h2 style={s.h2}>Authenticator app (TOTP)</h2>
            <p style={{ marginTop: 0 }}>Generate a secret and add it to an authenticator app. Future logins will require the 6-digit code.</p>
            <button onClick={enrollTotp} disabled={isWorking}>{isWorking ? 'Working…' : 'Generate TOTP secret'}</button>
            {totpSecret && (
              <div style={{ marginTop: 12 }}>
                <div><strong>Secret:</strong> <code>{totpSecret}</code></div>
                {totpUri && <div style={{ marginTop: 8 }}><strong>Provisioning URI:</strong><br /><code style={{ wordBreak: 'break-all' }}>{totpUri}</code></div>}
              </div>
            )}
          </section>

          {/* Sessions */}
          <section style={s.section}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <h2 style={s.h2}>Sessions</h2>
              <button onClick={loadSessions}>Refresh</button>
            </div>
            {sessions.length === 0
              ? <p>No active sessions.</p>
              : <ul>{sessions.map(s => (
                  <li key={s.id} style={{ marginBottom: 8 }}>
                    <div><strong>{s.id}</strong></div>
                    <div>Started: {new Date(s.started).toLocaleString()}</div>
                    <div>Last refresh: {new Date(s.lastRefresh).toLocaleString()}</div>
                    <button onClick={() => deleteSession(s.id)} style={{ marginTop: 4 }}>Delete</button>
                  </li>
                ))}</ul>}
          </section>
        </>
      ) : (
        <p style={{ color: '#6c757d' }}>Sign in above to manage your account.</p>
      )}
    </div>
  )
}
