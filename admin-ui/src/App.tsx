import React, { useEffect, useMemo, useState } from 'react'
import '@patternfly/react-core/dist/styles/base.css'

type Realm = { id: string; name: string; displayName?: string; enabled?: boolean }
type User = { id: string; realmId: string; username: string; email?: string; enabled?: boolean; emailVerified?: boolean }
type Client = { id: string; realmId: string; clientId: string; protocol: string; publicClient?: boolean }
type Role = { id: string; realmId: string; name: string; clientId?: string }
type Session = { id: string; userId: string; started: string; lastRefresh: string }

export default function App() {
  const [realms, setRealms] = useState<Realm[]>([])
  const [realmName, setRealmName] = useState('')
  const [realmDisplayName, setRealmDisplayName] = useState('')
  const [creatingRealm, setCreatingRealm] = useState(false)

  const [selectedRealmId, setSelectedRealmId] = useState<string | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [userUsername, setUserUsername] = useState('')
  const [userEmail, setUserEmail] = useState('')
  const [creatingUser, setCreatingUser] = useState(false)
  const [passwordInputs, setPasswordInputs] = useState<Record<string, string>>({})

  const [clients, setClients] = useState<Client[]>([])
  const [clientIdInput, setClientIdInput] = useState('')
  const [clientProtocol, setClientProtocol] = useState('openid-connect')
  const [clientPublic, setClientPublic] = useState(true)
  const [creatingClient, setCreatingClient] = useState(false)

  const [roles, setRoles] = useState<Role[]>([])
  const [roleName, setRoleName] = useState('')
  const [creatingRole, setCreatingRole] = useState(false)
  const [sessions, setSessions] = useState<Session[]>([])

  const selectedRealm = useMemo(() => realms.find(r => r.id === selectedRealmId) || null, [realms, selectedRealmId])

  const loadRealms = async () => {
    const res = await fetch('/admin/realms')
    const data = await res.json()
    setRealms(data)
    if (!selectedRealmId && data.length > 0) {
      setSelectedRealmId(data[0].id)
    }
  }

  const loadUsers = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/users`)
    const data = await res.json()
    setUsers(data)
  }

  const loadClients = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/clients`)
    const data = await res.json()
    setClients(data)
  }

  const loadRoles = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/roles`)
    const data = await res.json()
    setRoles(data)
  }

  const loadSessions = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/sessions`)
    const data = await res.json()
    setSessions(data)
  }

  const deleteSession = async (sessionId: string) => {
    if (!selectedRealmId) return
    await fetch(`/admin/realms/${selectedRealmId}/sessions/${sessionId}`, { method: 'DELETE' })
    await loadSessions(selectedRealmId)
  }

  useEffect(() => {
    loadRealms()
  }, [])

  useEffect(() => {
    if (selectedRealmId) {
      loadUsers(selectedRealmId)
      loadClients(selectedRealmId)
      loadRoles(selectedRealmId)
      loadSessions(selectedRealmId)
    } else {
      setUsers([])
      setClients([])
      setRoles([])
      setSessions([])
    }
  }, [selectedRealmId])

  const createRealm = async (e: React.FormEvent) => {
    e.preventDefault()
    setCreatingRealm(true)
    await fetch('/admin/realms', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: realmName, displayName: realmDisplayName })
    })
    setRealmName('')
    setRealmDisplayName('')
    await loadRealms()
    setCreatingRealm(false)
  }

  const createUser = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingUser(true)
    await fetch(`/admin/realms/${selectedRealmId}/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: userUsername, email: userEmail, enabled: true })
    })
    setUserUsername('')
    setUserEmail('')
    await loadUsers(selectedRealmId)
    setCreatingUser(false)
  }

  const setPassword = async (userId: string) => {
    if (!selectedRealmId) return
    const pwd = passwordInputs[userId]
    if (!pwd) return
    await fetch(`/admin/realms/${selectedRealmId}/users/${userId}/credentials/password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: pwd })
    })
    setPasswordInputs(prev => ({ ...prev, [userId]: '' }))
  }

  const createClient = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingClient(true)
    await fetch(`/admin/realms/${selectedRealmId}/clients`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ clientId: clientIdInput, protocol: clientProtocol, publicClient: clientPublic })
    })
    setClientIdInput('')
    await loadClients(selectedRealmId)
    setCreatingClient(false)
  }

  const createRole = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingRole(true)
    await fetch(`/admin/realms/${selectedRealmId}/roles`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: roleName })
    })
    setRoleName('')
    await loadRoles(selectedRealmId)
    setCreatingRole(false)
  }

  return (
    <div style={{ padding: 24, fontFamily: 'system-ui, sans-serif' }}>
      <h1>OpenIdentity Admin Console</h1>
      <div style={{ display: 'flex', gap: 32, alignItems: 'flex-start' }}>
        <section style={{ flex: 1 }}>
          <h2>Realms</h2>
          <div style={{ marginBottom: 12 }}>
            <label>
              Select realm:
              <select value={selectedRealmId ?? ''} onChange={e => setSelectedRealmId(e.target.value)} style={{ marginLeft: 8 }}>
                {realms.map(r => (
                  <option key={r.id} value={r.id}>{r.name}</option>
                ))}
              </select>
            </label>
          </div>
          <ul>
            {realms.map(r => (
              <li key={r.id}>
                <strong>{r.name}</strong> {r.displayName ? `— ${r.displayName}` : ''} {r.enabled ? '(enabled)' : '(disabled)'}
              </li>
            ))}
          </ul>
          <h3 style={{ marginTop: 16 }}>Create Realm</h3>
          <form onSubmit={createRealm}>
            <div>
              <label>
                Name:
                <input value={realmName} onChange={e => setRealmName(e.target.value)} required />
              </label>
            </div>
            <div>
              <label>
                Display Name:
                <input value={realmDisplayName} onChange={e => setRealmDisplayName(e.target.value)} />
              </label>
            </div>
            <button disabled={creatingRealm || !realmName}>Create</button>
          </form>
        </section>
        <section style={{ flex: 1 }}>
          <h2>Users {selectedRealm ? `in ${selectedRealm.name}` : ''}</h2>
          {selectedRealmId ? (
            <>
              <ul>
                {users.map(u => (
                  <li key={u.id} style={{ marginBottom: 6 }}>
                    <div>
                      <strong>{u.username}</strong> {u.email ? `— ${u.email}` : ''} {u.enabled ? '(enabled)' : '(disabled)'}
                    </div>
                    <div style={{ marginTop: 4 }}>
                      <input
                        type="password"
                        placeholder="Set password"
                        value={passwordInputs[u.id] ?? ''}
                        onChange={e => setPasswordInputs(prev => ({ ...prev, [u.id]: e.target.value }))}
                        style={{ marginRight: 8 }}
                      />
                      <button onClick={() => setPassword(u.id)} disabled={!passwordInputs[u.id]}>Set Password</button>
                    </div>
                  </li>
                ))}
              </ul>
              <h3 style={{ marginTop: 16 }}>Create User</h3>
              <form onSubmit={createUser}>
                <div>
                  <label>
                    Username:
                    <input value={userUsername} onChange={e => setUserUsername(e.target.value)} required />
                  </label>
                </div>
                <div>
                  <label>
                    Email:
                    <input type="email" value={userEmail} onChange={e => setUserEmail(e.target.value)} />
                  </label>
                </div>
                <button disabled={creatingUser || !userUsername}>Create</button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>
        <section style={{ flex: 1 }}>
          <h2>Clients</h2>
          {selectedRealmId ? (
            <>
              <ul>
                {clients.map(c => (
                  <li key={c.id}>
                    <strong>{c.clientId}</strong> — {c.protocol} {c.publicClient ? '(public)' : '(confidential)'}
                  </li>
                ))}
              </ul>
              <h3 style={{ marginTop: 16 }}>Create Client</h3>
              <form onSubmit={createClient}>
                <div>
                  <label>
                    Client ID:
                    <input value={clientIdInput} onChange={e => setClientIdInput(e.target.value)} required />
                  </label>
                </div>
                <div>
                  <label>
                    Protocol:
                    <select value={clientProtocol} onChange={e => setClientProtocol(e.target.value)}>
                      <option value="openid-connect">openid-connect</option>
                      <option value="saml">saml</option>
                    </select>
                  </label>
                </div>
                <div>
                  <label>
                    Public Client:
                    <input type="checkbox" checked={clientPublic} onChange={e => setClientPublic(e.target.checked)} />
                  </label>
                </div>
                <button disabled={creatingClient || !clientIdInput}>Create</button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>
        <section style={{ flex: 1 }}>
          <h2>Roles</h2>
          {selectedRealmId ? (
            <>
              <ul>
                {roles.map(r => (
                  <li key={r.id}>
                    <strong>{r.name}</strong> {r.clientId ? '(client role)' : '(realm role)'}
                  </li>
                ))}
              </ul>
              <h3 style={{ marginTop: 16 }}>Create Role</h3>
              <form onSubmit={createRole}>
                <div>
                  <label>
                    Role Name:
                    <input value={roleName} onChange={e => setRoleName(e.target.value)} required />
                  </label>
                </div>
                <button disabled={creatingRole || !roleName}>Create</button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>
        <section style={{ flex: 1 }}>
          <h2>Sessions</h2>
          {selectedRealmId ? (
            <>
              <ul>
                {sessions.map(s => (
                  <li key={s.id} style={{ marginBottom: 6 }}>
                    <div>
                      <strong>{s.id}</strong> — user {s.userId} — started {new Date(s.started).toLocaleString()}
                    </div>
                    <div>last refresh {new Date(s.lastRefresh).toLocaleString()}</div>
                    <button onClick={() => deleteSession(s.id)} style={{ marginTop: 4 }}>Delete</button>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>
      </div>
    </div>
  )
}

