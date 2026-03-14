import React, { useEffect, useMemo, useState } from 'react'
import '@patternfly/react-core/dist/styles/base.css'

type Realm = { id: string; name: string; displayName?: string; enabled?: boolean }
type User = { id: string; realmId: string; username: string; email?: string; enabled?: boolean; emailVerified?: boolean }
type Client = {
  id: string
  realmId: string
  clientId: string
  protocol: string
  publicClient?: boolean
  redirectUris?: string[]
  grantTypes?: string[]
}
type Role = { id: string; realmId: string; name: string; clientId?: string }
type Session = { id: string; userId: string; started: string; lastRefresh: string }
type LoginEvent = {
  id: number
  type: string
  time: string | null
  userId?: string | null
  clientId?: string | null
  ipAddress?: string | null
  details?: string | null
}
type AdminAuditEvent = {
  id: number
  action: string
  resourceType?: string | null
  resourceId?: string | null
  time: string | null
  actorUserId?: string | null
  ipAddress?: string | null
  details?: string | null
}

const parseLines = (value: string) =>
  value
    .split(/\r?\n|,/)
    .map(item => item.trim())
    .filter(Boolean)

export default function App() {
  const [accessToken, setAccessToken] = useState('')
  const [realms, setRealms] = useState<Realm[]>([])
  const [realmName, setRealmName] = useState('')
  const [realmDisplayName, setRealmDisplayName] = useState('')
  const [creatingRealm, setCreatingRealm] = useState(false)

  const [selectedRealmId, setSelectedRealmId] = useState<string | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [userUsername, setUserUsername] = useState('')
  const [userEmail, setUserEmail] = useState('')
  const [creatingUser, setCreatingUser] = useState(false)
  const [userEmailInputs, setUserEmailInputs] = useState<Record<string, string>>({})
  const [userEnabledInputs, setUserEnabledInputs] = useState<Record<string, boolean>>({})
  const [passwordInputs, setPasswordInputs] = useState<Record<string, string>>({})
  const [userRoleIdsByUser, setUserRoleIdsByUser] = useState<Record<string, string[]>>({})
  const [userRoleLoading, setUserRoleLoading] = useState<Record<string, boolean>>({})

  const [clients, setClients] = useState<Client[]>([])
  const [clientIdInput, setClientIdInput] = useState('')
  const [clientProtocol, setClientProtocol] = useState('openid-connect')
  const [clientPublic, setClientPublic] = useState(true)
  const [clientSecret, setClientSecret] = useState('')
  const [redirectUrisInput, setRedirectUrisInput] = useState('')
  const [grantTypesInput, setGrantTypesInput] = useState('authorization_code\nrefresh_token')
  const [creatingClient, setCreatingClient] = useState(false)

  const [roles, setRoles] = useState<Role[]>([])
  const [roleName, setRoleName] = useState('')
  const [roleClientId, setRoleClientId] = useState('')
  const [creatingRole, setCreatingRole] = useState(false)
  const [sessions, setSessions] = useState<Session[]>([])
  const [loginEvents, setLoginEvents] = useState<LoginEvent[]>([])
  const [adminEvents, setAdminEvents] = useState<AdminAuditEvent[]>([])
  const [eventsLoading, setEventsLoading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const selectedRealm = useMemo(() => realms.find(r => r.id === selectedRealmId) || null, [realms, selectedRealmId])

  const authHeaders = (json = false) => ({
    Authorization: `Bearer ${accessToken.trim()}`,
    ...(json ? { 'Content-Type': 'application/json' } : {})
  })

  const setFeedback = (nextMessage: string | null, nextError: string | null) => {
    setMessage(nextMessage)
    setError(nextError)
  }

  const loadRealms = async () => {
    const res = await fetch('/admin/realms', { headers: authHeaders() })
    if (!res.ok) {
      setRealms([])
      return
    }
    const data = await res.json()
    setRealms(data)
    if (!selectedRealmId && data.length > 0) {
      setSelectedRealmId(data[0].id)
    }
  }

  const loadUsers = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/users`, { headers: authHeaders() })
    if (!res.ok) {
      setUsers([])
      setUserEmailInputs({})
      setUserEnabledInputs({})
      setUserRoleIdsByUser({})
      setUserRoleLoading({})
      return
    }
    const data: User[] = await res.json()
    setUsers(data)
    setUserEmailInputs(Object.fromEntries(data.map(user => [user.id, user.email ?? ''])))
    setUserEnabledInputs(Object.fromEntries(data.map(user => [user.id, Boolean(user.enabled)])))
    if (data.length === 0) {
      setUserRoleIdsByUser({})
      setUserRoleLoading({})
      return
    }
    setUserRoleLoading(Object.fromEntries(data.map(user => [user.id, true])))
    const roleEntries = await Promise.all(
      data.map(async user => {
        const roleRes = await fetch(`/admin/realms/${realmId}/users/${user.id}/roles`, { headers: authHeaders() })
        if (!roleRes.ok) {
          return [user.id, []] as const
        }
        const assignedRoles: Role[] = await roleRes.json()
        return [user.id, assignedRoles.map(role => role.id)] as const
      })
    )
    setUserRoleIdsByUser(Object.fromEntries(roleEntries))
    setUserRoleLoading(Object.fromEntries(data.map(user => [user.id, false])))
  }

  const loadClients = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/clients`, { headers: authHeaders() })
    if (!res.ok) {
      setClients([])
      return
    }
    setClients(await res.json())
  }

  const loadRoles = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/roles`, { headers: authHeaders() })
    if (!res.ok) {
      setRoles([])
      return
    }
    setRoles(await res.json())
  }

  const loadSessions = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/sessions`, { headers: authHeaders() })
    if (!res.ok) {
      setSessions([])
      return
    }
    setSessions(await res.json())
  }

  const loadEvents = async (realmId: string) => {
    setEventsLoading(true)
    try {
      const [loginRes, adminRes] = await Promise.all([
        fetch(`/admin/realms/${realmId}/events/logins`, { headers: authHeaders() }),
        fetch(`/admin/realms/${realmId}/events/admin`, { headers: authHeaders() })
      ])
      if (!loginRes.ok || !adminRes.ok) {
        setLoginEvents([])
        setAdminEvents([])
        return
      }
      const [loginData, adminData] = await Promise.all([loginRes.json(), adminRes.json()])
      setLoginEvents(loginData)
      setAdminEvents(adminData)
    } finally {
      setEventsLoading(false)
    }
  }

  const refreshSelectedRealm = async (realmId: string) => {
    await Promise.all([loadUsers(realmId), loadClients(realmId), loadRoles(realmId), loadSessions(realmId), loadEvents(realmId)])
  }

  const deleteSession = async (sessionId: string) => {
    if (!selectedRealmId) return
    const confirmed = window.confirm('Delete this session?')
    if (!confirmed) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/sessions/${sessionId}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete session.')
      return
    }
    await loadSessions(selectedRealmId)
    setFeedback('Session deleted.', null)
  }

  useEffect(() => {
    if (accessToken.trim()) {
      loadRealms()
    } else {
      setRealms([])
      setSelectedRealmId(null)
      setUsers([])
      setUserEmailInputs({})
      setUserEnabledInputs({})
      setClients([])
      setRoles([])
      setSessions([])
      setUserRoleIdsByUser({})
      setUserRoleLoading({})
      setLoginEvents([])
      setAdminEvents([])
      setEventsLoading(false)
      setFeedback(null, null)
    }
  }, [accessToken])

  useEffect(() => {
    if (selectedRealmId) {
      refreshSelectedRealm(selectedRealmId)
    } else {
      setUsers([])
      setUserEmailInputs({})
      setUserEnabledInputs({})
      setClients([])
      setRoles([])
      setSessions([])
      setUserRoleIdsByUser({})
      setUserRoleLoading({})
      setLoginEvents([])
      setAdminEvents([])
      setEventsLoading(false)
    }
  }, [selectedRealmId])

  const createRealm = async (e: React.FormEvent) => {
    e.preventDefault()
    setCreatingRealm(true)
    setFeedback(null, null)
    try {
      const res = await fetch('/admin/realms', {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({ name: realmName, displayName: realmDisplayName })
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to create realm.')
        return
      }
      setRealmName('')
      setRealmDisplayName('')
      await loadRealms()
      setFeedback('Realm created.', null)
    } finally {
      setCreatingRealm(false)
    }
  }

  const createUser = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingUser(true)
    setFeedback(null, null)
    try {
      const res = await fetch(`/admin/realms/${selectedRealmId}/users`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({ username: userUsername, email: userEmail, enabled: true })
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to create user.')
        return
      }
      setUserUsername('')
      setUserEmail('')
      await loadUsers(selectedRealmId)
      setFeedback('User created.', null)
    } finally {
      setCreatingUser(false)
    }
  }

  const setPassword = async (userId: string) => {
    if (!selectedRealmId) return
    const pwd = passwordInputs[userId]
    if (!pwd) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/users/${userId}/credentials/password`, {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ password: pwd })
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to update password.')
      return
    }
    setPasswordInputs(prev => ({ ...prev, [userId]: '' }))
    setFeedback('Password updated.', null)
  }

  const updateUser = async (userId: string) => {
    if (!selectedRealmId) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/users/${userId}`, {
      method: 'PUT',
      headers: authHeaders(true),
      body: JSON.stringify({
        email: userEmailInputs[userId] ?? '',
        enabled: Boolean(userEnabledInputs[userId])
      })
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to update user.')
      return
    }
    await loadUsers(selectedRealmId)
    setFeedback('User updated.', null)
  }

  const deleteUser = async (userId: string) => {
    if (!selectedRealmId) return
    if (!window.confirm('Delete this user?')) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/users/${userId}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete user.')
      return
    }
    await Promise.all([loadUsers(selectedRealmId), loadSessions(selectedRealmId), loadEvents(selectedRealmId)])
    setFeedback('User deleted.', null)
  }

  const toggleRoleAssignment = async (userId: string, roleId: string, assigned: boolean) => {
    if (!selectedRealmId) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/users/${userId}/roles/${roleId}`, {
      method: assigned ? 'DELETE' : 'POST',
      headers: authHeaders(assigned ? false : true),
      ...(assigned ? {} : { body: '{}' })
    })
    if (!res.ok) {
      setFeedback(null, assigned ? 'Failed to remove role.' : 'Failed to assign role.')
      return
    }
    setUserRoleIdsByUser(prev => {
      const current = new Set(prev[userId] ?? [])
      if (assigned) {
        current.delete(roleId)
      } else {
        current.add(roleId)
      }
      return { ...prev, [userId]: Array.from(current) }
    })
    setFeedback(assigned ? 'Role removed from user.' : 'Role assigned to user.', null)
  }

  const createClient = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingClient(true)
    setFeedback(null, null)
    try {
      const payload = {
        clientId: clientIdInput,
        protocol: clientProtocol,
        publicClient: clientPublic,
        secret: clientPublic ? undefined : clientSecret,
        redirectUris: parseLines(redirectUrisInput),
        grantTypes: parseLines(grantTypesInput)
      }
      const res = await fetch(`/admin/realms/${selectedRealmId}/clients`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify(payload)
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to create client.')
        return
      }
      setClientIdInput('')
      setClientSecret('')
      setRedirectUrisInput('')
      setGrantTypesInput('authorization_code\nrefresh_token')
      await loadClients(selectedRealmId)
      setFeedback('Client created.', null)
    } finally {
      setCreatingClient(false)
    }
  }

  const createRole = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingRole(true)
    setFeedback(null, null)
    try {
      const res = await fetch(`/admin/realms/${selectedRealmId}/roles`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({ name: roleName, clientId: roleClientId || undefined })
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to create role.')
        return
      }
      setRoleName('')
      setRoleClientId('')
      await loadRoles(selectedRealmId)
      setFeedback('Role created.', null)
    } finally {
      setCreatingRole(false)
    }
  }

  const deleteClient = async (clientId: string) => {
    if (!selectedRealmId) return
    if (!window.confirm('Delete this client?')) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/clients/${clientId}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete client.')
      return
    }
    await loadClients(selectedRealmId)
    setFeedback('Client deleted.', null)
  }

  const deleteRole = async (roleId: string) => {
    if (!selectedRealmId) return
    if (!window.confirm('Delete this role?')) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/roles/${roleId}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete role.')
      return
    }
    await Promise.all([loadRoles(selectedRealmId), loadUsers(selectedRealmId)])
    setFeedback('Role deleted.', null)
  }

  return (
    <div style={{ padding: 24, fontFamily: 'system-ui, sans-serif' }}>
      <h1>OpenIdentity Admin Console</h1>
      <p style={{ marginTop: 0 }}>
        Provide an admin bearer token or the configured bootstrap bearer token to use the admin APIs.
      </p>
      <div style={{ marginBottom: 16 }}>
        <textarea
          placeholder="Admin access token or bootstrap token"
          value={accessToken}
          onChange={e => setAccessToken(e.target.value)}
          style={{ width: '100%', minHeight: 72 }}
        />
      </div>
      {message && <p style={{ color: '#0f5132' }}>{message}</p>}
      {error && <p style={{ color: '#842029' }}>{error}</p>}

      <div style={{ display: 'flex', gap: 32, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <section style={{ flex: '1 1 260px' }}>
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
                <strong>{r.name}</strong> {r.displayName ? `- ${r.displayName}` : ''} {r.enabled ? '(enabled)' : '(disabled)'}
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
            <button disabled={creatingRealm || !realmName || !accessToken.trim()}>Create</button>
          </form>
        </section>

        <section style={{ flex: '1 1 260px' }}>
          <h2>Users {selectedRealm ? `in ${selectedRealm.name}` : ''}</h2>
          {selectedRealmId ? (
            <>
              <ul>
                {users.map(u => (
                  <li key={u.id} style={{ marginBottom: 6 }}>
                    <div>
                      <strong>{u.username}</strong> {u.email ? `- ${u.email}` : ''} {u.enabled ? '(enabled)' : '(disabled)'}
                    </div>
                    <div style={{ marginTop: 4 }}>
                      <label>
                        Email:
                        <input
                          type="email"
                          value={userEmailInputs[u.id] ?? ''}
                          onChange={e => setUserEmailInputs(prev => ({ ...prev, [u.id]: e.target.value }))}
                          style={{ marginLeft: 8 }}
                        />
                      </label>
                    </div>
                    <div style={{ marginTop: 4 }}>
                      <label>
                        Enabled:
                        <input
                          type="checkbox"
                          checked={Boolean(userEnabledInputs[u.id])}
                          onChange={e => setUserEnabledInputs(prev => ({ ...prev, [u.id]: e.target.checked }))}
                          style={{ marginLeft: 8 }}
                        />
                      </label>
                      <button onClick={() => updateUser(u.id)} style={{ marginLeft: 8 }}>Save</button>
                      <button onClick={() => deleteUser(u.id)} style={{ marginLeft: 8 }}>Delete</button>
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
                    <div style={{ marginTop: 8 }}>
                      <strong>Roles</strong>
                      {userRoleLoading[u.id] ? (
                        <div>Loading roles...</div>
                      ) : roles.length === 0 ? (
                        <div>No roles available.</div>
                      ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 4 }}>
                          {roles.map(role => {
                            const assigned = (userRoleIdsByUser[u.id] ?? []).includes(role.id)
                            return (
                              <label key={role.id}>
                                <input
                                  type="checkbox"
                                  checked={assigned}
                                  onChange={() => toggleRoleAssignment(u.id, role.id, assigned)}
                                />
                                <span style={{ marginLeft: 8 }}>
                                  {role.name} {role.clientId ? '(client role)' : '(realm role)'}
                                </span>
                              </label>
                            )
                          })}
                        </div>
                      )}
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
                <button disabled={creatingUser || !userUsername || !accessToken.trim()}>Create</button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>

        <section style={{ flex: '1 1 320px' }}>
          <h2>Clients</h2>
          {selectedRealmId ? (
            <>
              <ul>
                {clients.map(c => (
                  <li key={c.id} style={{ marginBottom: 10 }}>
                    <div>
                      <strong>{c.clientId}</strong> - {c.protocol} {c.publicClient ? '(public)' : '(confidential)'}
                    </div>
                    <div>Grant types: {(c.grantTypes ?? []).join(', ') || 'default'}</div>
                    <div>Redirect URIs: {(c.redirectUris ?? []).join(', ') || 'none'}</div>
                    <button onClick={() => deleteClient(c.id)} style={{ marginTop: 4 }}>Delete</button>
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
                {!clientPublic && (
                  <div>
                    <label>
                      Client Secret:
                      <input type="password" value={clientSecret} onChange={e => setClientSecret(e.target.value)} />
                    </label>
                  </div>
                )}
                <div>
                  <label>
                    Redirect URIs:
                    <textarea
                      value={redirectUrisInput}
                      onChange={e => setRedirectUrisInput(e.target.value)}
                      placeholder="One URI per line"
                      style={{ width: '100%', minHeight: 72 }}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Grant Types:
                    <textarea
                      value={grantTypesInput}
                      onChange={e => setGrantTypesInput(e.target.value)}
                      placeholder="One grant type per line"
                      style={{ width: '100%', minHeight: 72 }}
                    />
                  </label>
                </div>
                <button disabled={creatingClient || !clientIdInput || !accessToken.trim()}>Create</button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>

        <section style={{ flex: '1 1 240px' }}>
          <h2>Roles</h2>
          {selectedRealmId ? (
            <>
              <ul>
                {roles.map(r => (
                  <li key={r.id} style={{ marginBottom: 6 }}>
                    <strong>{r.name}</strong> {r.clientId ? '(client role)' : '(realm role)'}
                    <button onClick={() => deleteRole(r.id)} style={{ marginLeft: 8 }}>Delete</button>
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
                <div>
                  <label>
                    Client Scope:
                    <select value={roleClientId} onChange={e => setRoleClientId(e.target.value)} style={{ marginLeft: 8 }}>
                      <option value="">Realm role</option>
                      {clients.map(client => (
                        <option key={client.id} value={client.id}>{client.clientId}</option>
                      ))}
                    </select>
                  </label>
                </div>
                <button disabled={creatingRole || !roleName || !accessToken.trim()}>Create</button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>

        <section style={{ flex: '1 1 280px' }}>
          <h2>Sessions</h2>
          {selectedRealmId ? (
            sessions.length === 0 ? (
              <p>No sessions found.</p>
            ) : (
              <ul>
                {sessions.map(s => (
                  <li key={s.id} style={{ marginBottom: 6 }}>
                    <div>
                      <strong>{s.id}</strong> - user {s.userId}
                    </div>
                    <div>started {new Date(s.started).toLocaleString()}</div>
                    <div>last refresh {new Date(s.lastRefresh).toLocaleString()}</div>
                    <button onClick={() => deleteSession(s.id)} style={{ marginTop: 4 }}>Delete</button>
                  </li>
                ))}
              </ul>
            )
          ) : (
            <p>No realm selected</p>
          )}
        </section>

        <section style={{ flex: '1 1 420px' }}>
          <h2>Events</h2>
          {selectedRealmId ? (
            eventsLoading ? (
              <p>Loading event activity...</p>
            ) : (
              <>
                <h3>Recent Login Events</h3>
                {loginEvents.length === 0 ? (
                  <p>No login events found.</p>
                ) : (
                  <ul>
                    {loginEvents.map(event => (
                      <li key={event.id} style={{ marginBottom: 8 }}>
                        <div>
                          <strong>{event.type}</strong> {event.time ? `- ${new Date(event.time).toLocaleString()}` : ''}
                        </div>
                        <div>User {event.userId ?? 'unknown'} via client {event.clientId ?? 'n/a'}</div>
                        <div>IP {event.ipAddress ?? 'n/a'}</div>
                        {event.details ? <div>{event.details}</div> : null}
                      </li>
                    ))}
                  </ul>
                )}

                <h3 style={{ marginTop: 16 }}>Recent Admin Events</h3>
                {adminEvents.length === 0 ? (
                  <p>No admin audit events found.</p>
                ) : (
                  <ul>
                    {adminEvents.map(event => (
                      <li key={event.id} style={{ marginBottom: 8 }}>
                        <div>
                          <strong>{event.action}</strong> {event.resourceType ? `- ${event.resourceType}` : ''}
                          {event.time ? ` - ${new Date(event.time).toLocaleString()}` : ''}
                        </div>
                        <div>Actor {event.actorUserId ?? 'unknown'} on resource {event.resourceId ?? 'n/a'}</div>
                        <div>IP {event.ipAddress ?? 'n/a'}</div>
                        {event.details ? <div>{event.details}</div> : null}
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )
          ) : (
            <p>No realm selected</p>
          )}
        </section>
      </div>
    </div>
  )
}
