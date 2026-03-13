import React, { useEffect, useMemo, useState } from 'react'

type User = { id: string; realmId: string; username: string; email?: string; enabled?: boolean; emailVerified?: boolean }
type Session = { id: string; userId: string; started: string; lastRefresh: string }

export default function App() {
  const [realmId, setRealmId] = useState('')
  const [userId, setUserId] = useState('')
  const [user, setUser] = useState<User | null>(null)
  const [email, setEmail] = useState('')
  const [sessions, setSessions] = useState<Session[]>([])
  const [newPassword, setNewPassword] = useState('')
  const [totpSecret, setTotpSecret] = useState<string | null>(null)
  const [totpUri, setTotpUri] = useState<string | null>(null)
  const [isEnrollingTotp, setIsEnrollingTotp] = useState(false)
  const canLoad = realmId && userId

  const loadUser = async () => {
    const res = await fetch(`/admin/realms/${realmId}/users/${userId}`)
    if (res.ok) {
      const data = await res.json()
      setUser(data)
      setEmail(data.email ?? '')
    } else {
      setUser(null)
    }
  }
  const saveEmail = async () => {
    await fetch(`/admin/realms/${realmId}/users/${userId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email })
    })
    await loadUser()
  }
  const updatePassword = async () => {
    if (!newPassword) return
    await fetch(`/admin/realms/${realmId}/users/${userId}/credentials/password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: newPassword })
    })
    setNewPassword('')
  }
  const loadSessions = async () => {
    const res = await fetch(`/admin/realms/${realmId}/sessions`)
    if (!res.ok) { setSessions([]); return }
    const all: Session[] = await res.json()
    setSessions(all.filter(s => s.userId === userId))
  }
  const deleteSession = async (sid: string) => {
    await fetch(`/admin/realms/${realmId}/sessions/${sid}`, { method: 'DELETE' })
    await loadSessions()
  }

  const enrollTotp = async () => {
    if (!realmId || !userId) return
    setIsEnrollingTotp(true)
    try {
      const res = await fetch(`/admin/realms/${realmId}/users/${userId}/credentials/totp`, {
        method: 'POST'
      })
      if (res.ok) {
        const data = await res.json()
        setTotpSecret(data.secret)
        setTotpUri(data.provisioningUri)
      }
    } finally {
      setIsEnrollingTotp(false)
    }
  }

  useEffect(() => {
    setUser(null)
    setSessions([])
  }, [realmId, userId])

  return (
    <div style={{ padding: 24, fontFamily: 'system-ui, sans-serif', maxWidth: 900, margin: '0 auto' }}>
      <h1>Account</h1>
      <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
        <input placeholder="Realm ID" value={realmId} onChange={e => setRealmId(e.target.value)} style={{ flex: 1 }} />
        <input placeholder="User ID" value={userId} onChange={e => setUserId(e.target.value)} style={{ flex: 1 }} />
        <button disabled={!canLoad} onClick={loadUser}>Load</button>
      </div>
      {user ? (
        <>
          <section style={{ marginBottom: 24 }}>
            <h2>Profile</h2>
            <div><strong>Username:</strong> {user.username}</div>
            <div><strong>Email:</strong> <input value={email} onChange={e => setEmail(e.target.value)} /> <button onClick={saveEmail}>Save</button></div>
            <div><strong>Enabled:</strong> {user.enabled ? 'Yes' : 'No'}</div>
            <div><strong>Email Verified:</strong> {user.emailVerified ? 'Yes' : 'No'}</div>
          </section>
          <section style={{ marginBottom: 24 }}>
            <h2>Change Password</h2>
            <input type="password" placeholder="New password" value={newPassword} onChange={e => setNewPassword(e.target.value)} />
            <button disabled={!newPassword} onClick={updatePassword} style={{ marginLeft: 8 }}>Update</button>
          </section>
          <section style={{ marginBottom: 24 }}>
            <h2>Multi-factor Authentication (TOTP)</h2>
            <p>Generate a TOTP secret to add this account to an authenticator app (e.g. Google Authenticator, Authy).</p>
            <button onClick={enrollTotp} disabled={isEnrollingTotp}>
              {isEnrollingTotp ? 'Generating…' : 'Generate TOTP Secret'}
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
                <p style={{ marginTop: 8, fontSize: 12 }}>
                  After adding this secret to your authenticator app, future logins using the password grant
                  must include a valid 6-digit TOTP code.
                </p>
              </div>
            )}
          </section>
          <section>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <h2 style={{ margin: 0 }}>Sessions</h2>
              <button onClick={loadSessions}>Refresh</button>
            </div>
            <ul>
              {sessions.map(s => (
                <li key={s.id} style={{ marginBottom: 6 }}>
                  <div><strong>{s.id}</strong> — started {new Date(s.started).toLocaleString()}</div>
                  <div>last refresh {new Date(s.lastRefresh).toLocaleString()}</div>
                  <button onClick={() => deleteSession(s.id)} style={{ marginTop: 4 }}>Delete</button>
                </li>
              ))}
            </ul>
          </section>
        </>
      ) : (
        <p>Enter Realm ID and User ID to load your profile.</p>
      )}
    </div>
  )
}

