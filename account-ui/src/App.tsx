import React, { useEffect, useState } from 'react'

type User = {
  id: string
  realmId: string
  username: string
  email?: string
  enabled?: boolean
  emailVerified?: boolean
  federationSource?: string | null
}

type Session = {
  id: string
  started: string
  lastRefresh: string
}

type TokenResponse = {
  access_token: string
  refresh_token?: string
  id_token?: string
  expires_in: number
}

export default function App() {
  const [realm, setRealm] = useState('demo')
  const [clientId, setClientId] = useState('web-app')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const [accessToken, setAccessToken] = useState('')
  const [refreshToken, setRefreshToken] = useState('')
  const [user, setUser] = useState<User | null>(null)
  const [email, setEmail] = useState('')
  const [sessions, setSessions] = useState<Session[]>([])
  const [newPassword, setNewPassword] = useState('')
  const [totpSecret, setTotpSecret] = useState<string | null>(null)
  const [totpUri, setTotpUri] = useState<string | null>(null)
  const [isWorking, setIsWorking] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const canUseAccount = accessToken.trim().length > 0
  const externallyManaged = Boolean(user?.federationSource)

  const authHeaders = (json = false) => ({
    Authorization: `Bearer ${accessToken.trim()}`,
    ...(json ? { 'Content-Type': 'application/json' } : {})
  })

  const setFeedback = (nextMessage: string | null, nextError: string | null) => {
    setMessage(nextMessage)
    setError(nextError)
  }

  const loadUser = async () => {
    if (!canUseAccount) return
    const res = await fetch('/account/profile', { headers: authHeaders() })
    if (!res.ok) {
      setUser(null)
      setEmail('')
      setFeedback(null, 'Failed to load account profile.')
      return
    }
    const data = await res.json()
    setUser(data)
    setEmail(data.email ?? '')
    setFeedback(null, null)
  }

  const loadSessions = async () => {
    if (!canUseAccount) return
    const res = await fetch('/account/sessions', { headers: authHeaders() })
    if (!res.ok) {
      setSessions([])
      setFeedback(null, 'Failed to load sessions.')
      return
    }
    const all: Session[] = await res.json()
    setSessions(all)
  }

  const login = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsWorking(true)
    setFeedback(null, null)
    try {
      const body = new URLSearchParams()
      body.set('grant_type', 'password')
      body.set('client_id', clientId)
      body.set('username', username)
      body.set('password', password)
      if (totpCode.trim()) {
        body.set('totp', totpCode.trim())
      }

      const res = await fetch(`/auth/realms/${encodeURIComponent(realm)}/protocol/openid-connect/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      })

      if (!res.ok) {
        setAccessToken('')
        setRefreshToken('')
        setUser(null)
        setSessions([])
        setFeedback(null, 'Sign-in failed. Check realm, client, credentials, and TOTP code.')
        return
      }

      const data: TokenResponse = await res.json()
      setAccessToken(data.access_token)
      setRefreshToken(data.refresh_token ?? '')
      setPassword('')
      setTotpCode('')
      setFeedback('Signed in successfully.', null)
    } finally {
      setIsWorking(false)
    }
  }

  const refreshSession = async () => {
    if (!refreshToken.trim()) {
      setFeedback(null, 'No refresh token is available for this session.')
      return
    }
    setIsWorking(true)
    setFeedback(null, null)
    try {
      const body = new URLSearchParams()
      body.set('grant_type', 'refresh_token')
      body.set('client_id', clientId)
      body.set('refresh_token', refreshToken)

      const res = await fetch(`/auth/realms/${encodeURIComponent(realm)}/protocol/openid-connect/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      })
      if (!res.ok) {
        setFeedback(null, 'Refresh failed. Sign in again.')
        return
      }
      const data: TokenResponse = await res.json()
      setAccessToken(data.access_token)
      setRefreshToken(data.refresh_token ?? '')
      setFeedback('Session refreshed.', null)
    } finally {
      setIsWorking(false)
    }
  }

  const logoutLocal = () => {
    setAccessToken('')
    setRefreshToken('')
    setUser(null)
    setSessions([])
    setTotpSecret(null)
    setTotpUri(null)
    setFeedback('Local session cleared.', null)
  }

  const saveEmail = async () => {
    const res = await fetch('/account/profile', {
      method: 'PUT',
      headers: authHeaders(true),
      body: JSON.stringify({ email })
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to update email.')
      return
    }
    await loadUser()
    setFeedback('Profile updated.', null)
  }

  const updatePassword = async () => {
    if (!newPassword) return
    const res = await fetch('/account/credentials/password', {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ password: newPassword })
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to update password.')
      return
    }
    setNewPassword('')
    setFeedback('Password updated.', null)
  }

  const deleteSession = async (sid: string) => {
    const confirmed = window.confirm('Delete this session?')
    if (!confirmed) return
    const res = await fetch(`/account/sessions/${sid}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete session.')
      return
    }
    await loadSessions()
    setFeedback('Session deleted.', null)
  }

  const enrollTotp = async () => {
    setIsWorking(true)
    setFeedback(null, null)
    try {
      const res = await fetch('/account/credentials/totp', {
        method: 'POST',
        headers: authHeaders()
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to generate TOTP secret.')
        return
      }
      const data = await res.json()
      setTotpSecret(data.secret)
      setTotpUri(data.provisioningUri)
      setFeedback('TOTP secret generated.', null)
    } finally {
      setIsWorking(false)
    }
  }

  useEffect(() => {
    if (!accessToken.trim()) {
      setUser(null)
      setSessions([])
      setTotpSecret(null)
      setTotpUri(null)
      return
    }
    loadUser()
    loadSessions()
  }, [accessToken])

  return (
    <div style={{ padding: 24, fontFamily: 'system-ui, sans-serif', maxWidth: 960, margin: '0 auto' }}>
      <h1>OpenIdentity Account</h1>
      <p style={{ marginTop: 0 }}>
        Sign in with the current password flow, then manage your own profile, password, MFA, and sessions.
      </p>

      <section style={{ marginBottom: 24, padding: 16, border: '1px solid #ddd', borderRadius: 8 }}>
        <h2 style={{ marginTop: 0 }}>Sign In</h2>
        <form onSubmit={login}>
          <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            <label>
              Realm
              <input value={realm} onChange={e => setRealm(e.target.value)} style={{ width: '100%' }} />
            </label>
            <label>
              Client ID
              <input value={clientId} onChange={e => setClientId(e.target.value)} style={{ width: '100%' }} />
            </label>
            <label>
              Username
              <input value={username} onChange={e => setUsername(e.target.value)} style={{ width: '100%' }} />
            </label>
            <label>
              Password
              <input type="password" value={password} onChange={e => setPassword(e.target.value)} style={{ width: '100%' }} />
            </label>
            <label>
              TOTP Code
              <input value={totpCode} onChange={e => setTotpCode(e.target.value)} style={{ width: '100%' }} />
            </label>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button disabled={isWorking || !realm || !clientId || !username || !password}>Sign In</button>
            <button type="button" disabled={!refreshToken || isWorking} onClick={refreshSession}>Refresh Session</button>
            <button type="button" disabled={!accessToken} onClick={logoutLocal}>Clear Session</button>
          </div>
        </form>
        {message && <p style={{ color: '#0f5132', marginBottom: 0 }}>{message}</p>}
        {error && <p style={{ color: '#842029', marginBottom: 0 }}>{error}</p>}
      </section>

      {user ? (
        <>
          <section style={{ marginBottom: 24, padding: 16, border: '1px solid #ddd', borderRadius: 8 }}>
            <h2 style={{ marginTop: 0 }}>Profile</h2>
            <div><strong>Username:</strong> {user.username}</div>
            <div><strong>Realm:</strong> {realm}</div>
            <div><strong>Identity Source:</strong> {user.federationSource ? user.federationSource.toUpperCase() : 'LOCAL'}</div>
            <div style={{ marginTop: 8 }}>
              <strong>Email:</strong>{' '}
              <input value={email} onChange={e => setEmail(e.target.value)} disabled={externallyManaged} />
              <button onClick={saveEmail} style={{ marginLeft: 8 }} disabled={externallyManaged}>Save</button>
            </div>
            {externallyManaged ? (
              <p style={{ marginBottom: 0 }}>
                Profile changes are managed by your external identity provider.
              </p>
            ) : null}
            <div><strong>Enabled:</strong> {user.enabled ? 'Yes' : 'No'}</div>
            <div><strong>Email Verified:</strong> {user.emailVerified ? 'Yes' : 'No'}</div>
          </section>

          <section style={{ marginBottom: 24, padding: 16, border: '1px solid #ddd', borderRadius: 8 }}>
            <h2 style={{ marginTop: 0 }}>Change Password</h2>
            {externallyManaged ? (
              <p style={{ marginBottom: 0 }}>
                Password changes are managed by your external identity provider.
              </p>
            ) : (
              <>
                <input
                  type="password"
                  placeholder="New password"
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                />
                <button disabled={!newPassword} onClick={updatePassword} style={{ marginLeft: 8 }}>
                  Update
                </button>
              </>
            )}
          </section>

          <section style={{ marginBottom: 24, padding: 16, border: '1px solid #ddd', borderRadius: 8 }}>
            <h2 style={{ marginTop: 0 }}>Multi-factor Authentication (TOTP)</h2>
            <p>Generate a secret and add it to an authenticator app. Future password logins for this user will require the TOTP code.</p>
            <button onClick={enrollTotp} disabled={isWorking}>
              {isWorking ? 'Working...' : 'Generate TOTP Secret'}
            </button>
            {totpSecret && (
              <div style={{ marginTop: 12 }}>
                <div><strong>Secret:</strong> <code>{totpSecret}</code></div>
                {totpUri && (
                  <div style={{ marginTop: 8 }}>
                    <div><strong>Provisioning URI:</strong></div>
                    <code style={{ wordBreak: 'break-all' }}>{totpUri}</code>
                  </div>
                )}
              </div>
            )}
          </section>

          <section style={{ padding: 16, border: '1px solid #ddd', borderRadius: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <h2 style={{ margin: 0 }}>Sessions</h2>
              <button onClick={loadSessions}>Refresh</button>
            </div>
            {sessions.length === 0 ? (
              <p>No active sessions found.</p>
            ) : (
              <ul>
                {sessions.map(s => (
                  <li key={s.id} style={{ marginBottom: 8 }}>
                    <div><strong>{s.id}</strong></div>
                    <div>Started: {new Date(s.started).toLocaleString()}</div>
                    <div>Last refresh: {new Date(s.lastRefresh).toLocaleString()}</div>
                    <button onClick={() => deleteSession(s.id)} style={{ marginTop: 4 }}>Delete Session</button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      ) : (
        <p>Sign in to load your account.</p>
      )}
    </div>
  )
}
