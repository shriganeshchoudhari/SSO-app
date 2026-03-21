import React, { useEffect, useMemo, useState } from 'react'
import '@patternfly/react-core/dist/styles/base.css'

type Realm = { id: string; name: string; displayName?: string; enabled?: boolean }
type User = {
  id: string
  realmId: string
  username: string
  email?: string
  enabled?: boolean
  emailVerified?: boolean
  federationSource?: string | null
  federationProviderId?: string | null
}
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
type LdapProvider = {
  id: string
  realmId: string
  name: string
  url: string
  bindDn?: string | null
  userSearchBase?: string | null
  userSearchFilter?: string | null
  usernameAttribute?: string | null
  emailAttribute?: string | null
  syncAttributesOnLogin?: boolean
  disableMissingUsers?: boolean
  enabled?: boolean
  bindCredentialConfigured?: boolean
}
type OidcBrokerProvider = {
  id: string
  realmId: string
  alias: string
  issuerUrl: string
  authorizationUrl?: string | null
  tokenUrl?: string | null
  userInfoUrl?: string | null
  jwksUrl?: string | null
  clientId: string
  scopes?: string[]
  usernameClaim?: string | null
  emailClaim?: string | null
  syncAttributesOnLogin?: boolean
  enabled?: boolean
  clientSecretConfigured?: boolean
}
type SamlBrokerProvider = {
  id: string
  realmId: string
  alias: string
  entityId: string
  ssoUrl: string
  sloUrl?: string | null
  nameIdFormat?: string | null
  syncAttributesOnLogin?: boolean
  wantAuthnRequestsSigned?: boolean
  enabled?: boolean
  x509CertificateConfigured?: boolean
}

const parseLines = (value: string) =>
  value
    .split(/\r?\n|,/)
    .map(item => item.trim())
    .filter(Boolean)

const isExternallyManagedUser = (user: User) => Boolean(user.federationSource)

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
  const [ldapProviders, setLdapProviders] = useState<LdapProvider[]>([])
  const [ldapName, setLdapName] = useState('')
  const [ldapUrl, setLdapUrl] = useState('ldap://directory.internal:389')
  const [ldapBindDn, setLdapBindDn] = useState('')
  const [ldapBindCredential, setLdapBindCredential] = useState('')
  const [ldapUserSearchBase, setLdapUserSearchBase] = useState('')
  const [ldapUserSearchFilter, setLdapUserSearchFilter] = useState('(uid={0})')
  const [ldapUsernameAttribute, setLdapUsernameAttribute] = useState('uid')
  const [ldapEmailAttribute, setLdapEmailAttribute] = useState('mail')
  const [ldapSyncAttributesOnLogin, setLdapSyncAttributesOnLogin] = useState(true)
  const [ldapDisableMissingUsers, setLdapDisableMissingUsers] = useState(false)
  const [ldapEnabled, setLdapEnabled] = useState(true)
  const [creatingLdapProvider, setCreatingLdapProvider] = useState(false)
  const [oidcProviders, setOidcProviders] = useState<OidcBrokerProvider[]>([])
  const [oidcAlias, setOidcAlias] = useState('')
  const [oidcIssuerUrl, setOidcIssuerUrl] = useState('https://accounts.example.com')
  const [oidcAuthorizationUrl, setOidcAuthorizationUrl] = useState('')
  const [oidcTokenUrl, setOidcTokenUrl] = useState('')
  const [oidcUserInfoUrl, setOidcUserInfoUrl] = useState('')
  const [oidcJwksUrl, setOidcJwksUrl] = useState('')
  const [oidcClientId, setOidcClientId] = useState('')
  const [oidcClientSecret, setOidcClientSecret] = useState('')
  const [oidcScopesInput, setOidcScopesInput] = useState('openid\nprofile\nemail')
  const [oidcUsernameClaim, setOidcUsernameClaim] = useState('preferred_username')
  const [oidcEmailClaim, setOidcEmailClaim] = useState('email')
  const [oidcSyncAttributesOnLogin, setOidcSyncAttributesOnLogin] = useState(true)
  const [oidcEnabled, setOidcEnabled] = useState(true)
  const [creatingOidcProvider, setCreatingOidcProvider] = useState(false)
  const [samlProviders, setSamlProviders] = useState<SamlBrokerProvider[]>([])
  const [samlAlias, setSamlAlias] = useState('')
  const [samlEntityId, setSamlEntityId] = useState('https://idp.example.com/metadata')
  const [samlSsoUrl, setSamlSsoUrl] = useState('https://idp.example.com/sso')
  const [samlSloUrl, setSamlSloUrl] = useState('')
  const [samlCertificate, setSamlCertificate] = useState('')
  const [samlNameIdFormat, setSamlNameIdFormat] = useState('urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress')
  const [samlSyncAttributesOnLogin, setSamlSyncAttributesOnLogin] = useState(true)
  const [samlWantAuthnRequestsSigned, setSamlWantAuthnRequestsSigned] = useState(false)
  const [samlEnabled, setSamlEnabled] = useState(true)
  const [creatingSamlProvider, setCreatingSamlProvider] = useState(false)
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

  const loadLdapProviders = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/federation/ldap`, { headers: authHeaders() })
    if (!res.ok) {
      setLdapProviders([])
      return
    }
    setLdapProviders(await res.json())
  }

  const loadOidcProviders = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/brokering/oidc`, { headers: authHeaders() })
    if (!res.ok) {
      setOidcProviders([])
      return
    }
    setOidcProviders(await res.json())
  }

  const loadSamlProviders = async (realmId: string) => {
    const res = await fetch(`/admin/realms/${realmId}/brokering/saml`, { headers: authHeaders() })
    if (!res.ok) {
      setSamlProviders([])
      return
    }
    setSamlProviders(await res.json())
  }

  const refreshSelectedRealm = async (realmId: string) => {
    await Promise.all([
      loadUsers(realmId),
      loadClients(realmId),
      loadRoles(realmId),
      loadSessions(realmId),
      loadEvents(realmId),
      loadLdapProviders(realmId),
      loadOidcProviders(realmId),
      loadSamlProviders(realmId)
    ])
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
      setLdapProviders([])
      setOidcProviders([])
      setSamlProviders([])
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
      setLdapProviders([])
      setOidcProviders([])
      setSamlProviders([])
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

  const detachFederation = async (userId: string) => {
    if (!selectedRealmId) return
    const pwd = passwordInputs[userId]
    if (!pwd) {
      setFeedback(null, 'Enter a local password before detaching this account.')
      return
    }
    if (!window.confirm('Detach this externally managed account and convert it to a local account?')) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/users/${userId}/detach-federation`, {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ password: pwd })
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to detach external identity.')
      return
    }
    setPasswordInputs(prev => ({ ...prev, [userId]: '' }))
    await loadUsers(selectedRealmId)
    setFeedback('External identity detached. User is now managed locally.', null)
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

  const createLdapProvider = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingLdapProvider(true)
    setFeedback(null, null)
    try {
      const res = await fetch(`/admin/realms/${selectedRealmId}/federation/ldap`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({
          name: ldapName,
          url: ldapUrl,
          bindDn: ldapBindDn || undefined,
          bindCredential: ldapBindCredential || undefined,
          userSearchBase: ldapUserSearchBase || undefined,
          userSearchFilter: ldapUserSearchFilter || undefined,
          usernameAttribute: ldapUsernameAttribute || undefined,
          emailAttribute: ldapEmailAttribute || undefined,
          syncAttributesOnLogin: ldapSyncAttributesOnLogin,
          disableMissingUsers: ldapDisableMissingUsers,
          enabled: ldapEnabled
        })
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to create LDAP provider.')
        return
      }
      setLdapName('')
      setLdapBindDn('')
      setLdapBindCredential('')
      setLdapUserSearchBase('')
      setLdapUserSearchFilter('(uid={0})')
      setLdapUsernameAttribute('uid')
      setLdapEmailAttribute('mail')
      setLdapSyncAttributesOnLogin(true)
      setLdapDisableMissingUsers(false)
      setLdapEnabled(true)
      await loadLdapProviders(selectedRealmId)
      setFeedback('LDAP provider created.', null)
    } finally {
      setCreatingLdapProvider(false)
    }
  }

  const deleteLdapProvider = async (providerId: string) => {
    if (!selectedRealmId) return
    if (!window.confirm('Delete this LDAP provider?')) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/federation/ldap/${providerId}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete LDAP provider.')
      return
    }
    await loadLdapProviders(selectedRealmId)
    setFeedback('LDAP provider deleted.', null)
  }

  const reconcileLdapProvider = async (providerId: string) => {
    if (!selectedRealmId) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/federation/ldap/${providerId}/reconcile`, {
      method: 'POST',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to reconcile LDAP provider.')
      return
    }
    const result = await res.json()
    await loadUsers(selectedRealmId)
    setFeedback(
      `LDAP reconcile complete. Checked ${result.checkedUsers}, updated ${result.updatedUsers}, disabled ${result.disabledUsers}.`,
      null
    )
  }

  const createOidcProvider = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingOidcProvider(true)
    setFeedback(null, null)
    try {
      const res = await fetch(`/admin/realms/${selectedRealmId}/brokering/oidc`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({
          alias: oidcAlias,
          issuerUrl: oidcIssuerUrl,
          authorizationUrl: oidcAuthorizationUrl || undefined,
          tokenUrl: oidcTokenUrl || undefined,
          userInfoUrl: oidcUserInfoUrl || undefined,
          jwksUrl: oidcJwksUrl || undefined,
          clientId: oidcClientId,
          clientSecret: oidcClientSecret || undefined,
          scopes: parseLines(oidcScopesInput),
          usernameClaim: oidcUsernameClaim || undefined,
          emailClaim: oidcEmailClaim || undefined,
          syncAttributesOnLogin: oidcSyncAttributesOnLogin,
          enabled: oidcEnabled
        })
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to create OIDC broker provider.')
        return
      }
      setOidcAlias('')
      setOidcAuthorizationUrl('')
      setOidcTokenUrl('')
      setOidcUserInfoUrl('')
      setOidcJwksUrl('')
      setOidcClientId('')
      setOidcClientSecret('')
      setOidcScopesInput('openid\nprofile\nemail')
      setOidcUsernameClaim('preferred_username')
      setOidcEmailClaim('email')
      setOidcSyncAttributesOnLogin(true)
      setOidcEnabled(true)
      await loadOidcProviders(selectedRealmId)
      setFeedback('OIDC broker provider created.', null)
    } finally {
      setCreatingOidcProvider(false)
    }
  }

  const deleteOidcProvider = async (providerId: string) => {
    if (!selectedRealmId) return
    if (!window.confirm('Delete this OIDC broker provider?')) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/brokering/oidc/${providerId}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete OIDC broker provider.')
      return
    }
    await loadOidcProviders(selectedRealmId)
    setFeedback('OIDC broker provider deleted.', null)
  }

  const createSamlProvider = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedRealmId) return
    setCreatingSamlProvider(true)
    setFeedback(null, null)
    try {
      const res = await fetch(`/admin/realms/${selectedRealmId}/brokering/saml`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({
          alias: samlAlias,
          entityId: samlEntityId,
          ssoUrl: samlSsoUrl,
          sloUrl: samlSloUrl || undefined,
          x509Certificate: samlCertificate || undefined,
          nameIdFormat: samlNameIdFormat || undefined,
          syncAttributesOnLogin: samlSyncAttributesOnLogin,
          wantAuthnRequestsSigned: samlWantAuthnRequestsSigned,
          enabled: samlEnabled
        })
      })
      if (!res.ok) {
        setFeedback(null, 'Failed to create SAML provider.')
        return
      }
      setSamlAlias('')
      setSamlSloUrl('')
      setSamlCertificate('')
      setSamlNameIdFormat('urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress')
      setSamlSyncAttributesOnLogin(true)
      setSamlWantAuthnRequestsSigned(false)
      setSamlEnabled(true)
      await loadSamlProviders(selectedRealmId)
      setFeedback('SAML provider created.', null)
    } finally {
      setCreatingSamlProvider(false)
    }
  }

  const deleteSamlProvider = async (providerId: string) => {
    if (!selectedRealmId) return
    if (!window.confirm('Delete this SAML provider?')) return
    const res = await fetch(`/admin/realms/${selectedRealmId}/brokering/saml/${providerId}`, {
      method: 'DELETE',
      headers: authHeaders()
    })
    if (!res.ok) {
      setFeedback(null, 'Failed to delete SAML provider.')
      return
    }
    await loadSamlProviders(selectedRealmId)
    setFeedback('SAML provider deleted.', null)
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
                      {u.federationSource ? ` [${u.federationSource}]` : ''}
                    </div>
                    <div style={{ marginTop: 4 }}>
                      <label>
                        Email:
                        <input
                          type="email"
                          value={userEmailInputs[u.id] ?? ''}
                          onChange={e => setUserEmailInputs(prev => ({ ...prev, [u.id]: e.target.value }))}
                          style={{ marginLeft: 8 }}
                          disabled={isExternallyManagedUser(u)}
                        />
                      </label>
                      {isExternallyManagedUser(u) ? (
                        <span style={{ marginLeft: 8 }}>
                          {u.federationSource === 'ldap' ? 'Directory-managed profile' : 'Externally managed profile'}
                        </span>
                      ) : null}
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
                      <button
                        onClick={() => updateUser(u.id)}
                        style={{ marginLeft: 8 }}
                        disabled={isExternallyManagedUser(u)}
                      >
                        Save
                      </button>
                      <button onClick={() => deleteUser(u.id)} style={{ marginLeft: 8 }}>Delete</button>
                    </div>
                    <div style={{ marginTop: 4 }}>
                      {isExternallyManagedUser(u) ? (
                        <>
                          <span>
                            Password is managed by {u.federationSource === 'ldap' ? 'LDAP' : 'the external identity provider'}.
                          </span>
                          <div style={{ marginTop: 6 }}>
                            <input
                              type="password"
                              placeholder="Set local password to detach"
                              value={passwordInputs[u.id] ?? ''}
                              onChange={e => setPasswordInputs(prev => ({ ...prev, [u.id]: e.target.value }))}
                              style={{ marginRight: 8 }}
                            />
                            <button onClick={() => detachFederation(u.id)} disabled={!passwordInputs[u.id]}>
                              Detach To Local
                            </button>
                          </div>
                        </>
                      ) : (
                        <>
                          <input
                            type="password"
                            placeholder="Set password"
                            value={passwordInputs[u.id] ?? ''}
                            onChange={e => setPasswordInputs(prev => ({ ...prev, [u.id]: e.target.value }))}
                            style={{ marginRight: 8 }}
                          />
                          <button onClick={() => setPassword(u.id)} disabled={!passwordInputs[u.id]}>Set Password</button>
                        </>
                      )}
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

        <section style={{ flex: '1 1 360px' }}>
          <h2>SAML Brokering</h2>
          {selectedRealmId ? (
            <>
              {samlProviders.length === 0 ? (
                <p>No SAML providers configured.</p>
              ) : (
                <ul>
                  {samlProviders.map(provider => (
                    <li key={provider.id} style={{ marginBottom: 10 }}>
                      <div>
                        <strong>{provider.alias}</strong> - {provider.entityId} {provider.enabled ? '(enabled)' : '(disabled)'}
                      </div>
                      <div>SSO URL: {provider.ssoUrl}</div>
                      <div>SLO URL: {provider.sloUrl || 'unset'}</div>
                      <div>NameID format: {provider.nameIdFormat || 'unset'}</div>
                      <div>
                        Sync attributes on login: {provider.syncAttributesOnLogin ? 'yes' : 'no'} | Want signed authn requests:{' '}
                        {provider.wantAuthnRequestsSigned ? 'yes' : 'no'}
                      </div>
                      <div>Certificate configured: {provider.x509CertificateConfigured ? 'yes' : 'no'}</div>
                      <button onClick={() => deleteSamlProvider(provider.id)} style={{ marginTop: 4 }}>Delete</button>
                    </li>
                  ))}
                </ul>
              )}

              <h3 style={{ marginTop: 16 }}>Add SAML Provider</h3>
              <form onSubmit={createSamlProvider}>
                <div>
                  <label>
                    Alias:
                    <input value={samlAlias} onChange={e => setSamlAlias(e.target.value)} required />
                  </label>
                </div>
                <div>
                  <label>
                    Entity ID:
                    <input value={samlEntityId} onChange={e => setSamlEntityId(e.target.value)} required style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    SSO URL:
                    <input value={samlSsoUrl} onChange={e => setSamlSsoUrl(e.target.value)} required style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    SLO URL:
                    <input value={samlSloUrl} onChange={e => setSamlSloUrl(e.target.value)} style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    NameID Format:
                    <input value={samlNameIdFormat} onChange={e => setSamlNameIdFormat(e.target.value)} style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    X.509 Certificate:
                    <textarea
                      value={samlCertificate}
                      onChange={e => setSamlCertificate(e.target.value)}
                      placeholder="Paste IdP signing certificate"
                      style={{ width: '100%', minHeight: 96 }}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Sync Attributes On Login:
                    <input
                      type="checkbox"
                      checked={samlSyncAttributesOnLogin}
                      onChange={e => setSamlSyncAttributesOnLogin(e.target.checked)}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Want Authn Requests Signed:
                    <input
                      type="checkbox"
                      checked={samlWantAuthnRequestsSigned}
                      onChange={e => setSamlWantAuthnRequestsSigned(e.target.checked)}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Enabled:
                    <input type="checkbox" checked={samlEnabled} onChange={e => setSamlEnabled(e.target.checked)} />
                  </label>
                </div>
                <button
                  disabled={creatingSamlProvider || !samlAlias || !samlEntityId || !samlSsoUrl || !accessToken.trim()}
                >
                  Create
                </button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>

        <section style={{ flex: '1 1 360px' }}>
          <h2>OIDC Brokering</h2>
          {selectedRealmId ? (
            <>
              {oidcProviders.length === 0 ? (
                <p>No OIDC broker providers configured.</p>
              ) : (
                <ul>
                  {oidcProviders.map(provider => (
                    <li key={provider.id} style={{ marginBottom: 10 }}>
                      <div>
                        <strong>{provider.alias}</strong> - {provider.issuerUrl} {provider.enabled ? '(enabled)' : '(disabled)'}
                      </div>
                      <div>Client ID: {provider.clientId}</div>
                      <div>
                        Endpoints: auth {provider.authorizationUrl || 'auto/unset'} | token {provider.tokenUrl || 'auto/unset'}
                      </div>
                      <div>
                        UserInfo {provider.userInfoUrl || 'auto/unset'} | JWKS {provider.jwksUrl || 'auto/unset'}
                      </div>
                      <div>Scopes: {(provider.scopes ?? []).join(', ') || 'openid'}</div>
                      <div>
                        Claims: username {provider.usernameClaim || 'preferred_username'} | email {provider.emailClaim || 'email'}
                      </div>
                      <div>
                        Sync attributes on login: {provider.syncAttributesOnLogin ? 'yes' : 'no'} | Client secret configured:{' '}
                        {provider.clientSecretConfigured ? 'yes' : 'no'}
                      </div>
                      <button onClick={() => deleteOidcProvider(provider.id)} style={{ marginTop: 4 }}>Delete</button>
                    </li>
                  ))}
                </ul>
              )}

              <h3 style={{ marginTop: 16 }}>Add OIDC Broker Provider</h3>
              <form onSubmit={createOidcProvider}>
                <div>
                  <label>
                    Alias:
                    <input value={oidcAlias} onChange={e => setOidcAlias(e.target.value)} required />
                  </label>
                </div>
                <div>
                  <label>
                    Issuer URL:
                    <input value={oidcIssuerUrl} onChange={e => setOidcIssuerUrl(e.target.value)} required style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    Authorization URL:
                    <input value={oidcAuthorizationUrl} onChange={e => setOidcAuthorizationUrl(e.target.value)} style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    Token URL:
                    <input value={oidcTokenUrl} onChange={e => setOidcTokenUrl(e.target.value)} style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    UserInfo URL:
                    <input value={oidcUserInfoUrl} onChange={e => setOidcUserInfoUrl(e.target.value)} style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    JWKS URL:
                    <input value={oidcJwksUrl} onChange={e => setOidcJwksUrl(e.target.value)} style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    Client ID:
                    <input value={oidcClientId} onChange={e => setOidcClientId(e.target.value)} required style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    Client Secret:
                    <input
                      type="password"
                      value={oidcClientSecret}
                      onChange={e => setOidcClientSecret(e.target.value)}
                      style={{ width: '100%' }}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Scopes:
                    <textarea
                      value={oidcScopesInput}
                      onChange={e => setOidcScopesInput(e.target.value)}
                      placeholder="One scope per line"
                      style={{ width: '100%', minHeight: 72 }}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Username Claim:
                    <input value={oidcUsernameClaim} onChange={e => setOidcUsernameClaim(e.target.value)} />
                  </label>
                </div>
                <div>
                  <label>
                    Email Claim:
                    <input value={oidcEmailClaim} onChange={e => setOidcEmailClaim(e.target.value)} />
                  </label>
                </div>
                <div>
                  <label>
                    Sync Attributes On Login:
                    <input
                      type="checkbox"
                      checked={oidcSyncAttributesOnLogin}
                      onChange={e => setOidcSyncAttributesOnLogin(e.target.checked)}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Enabled:
                    <input type="checkbox" checked={oidcEnabled} onChange={e => setOidcEnabled(e.target.checked)} />
                  </label>
                </div>
                <button
                  disabled={creatingOidcProvider || !oidcAlias || !oidcIssuerUrl || !oidcClientId || !accessToken.trim()}
                >
                  Create
                </button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>

        <section style={{ flex: '1 1 360px' }}>
          <h2>LDAP Federation</h2>
          {selectedRealmId ? (
            <>
              {ldapProviders.length === 0 ? (
                <p>No LDAP providers configured.</p>
              ) : (
                <ul>
                  {ldapProviders.map(provider => (
                    <li key={provider.id} style={{ marginBottom: 10 }}>
                      <div>
                        <strong>{provider.name}</strong> - {provider.url} {provider.enabled ? '(enabled)' : '(disabled)'}
                      </div>
                      <div>Bind DN: {provider.bindDn || 'anonymous or unset'}</div>
                      <div>Search base: {provider.userSearchBase || 'unset'}</div>
                      <div>Search filter: {provider.userSearchFilter || 'unset'}</div>
                      <div>
                        Username attr: {provider.usernameAttribute || 'uid'} | Email attr: {provider.emailAttribute || 'mail'}
                      </div>
                      <div>
                        Sync attributes on login: {provider.syncAttributesOnLogin ? 'yes' : 'no'} | Disable missing users: {provider.disableMissingUsers ? 'yes' : 'no'}
                      </div>
                      <div>Bind credential configured: {provider.bindCredentialConfigured ? 'yes' : 'no'}</div>
                      <button onClick={() => reconcileLdapProvider(provider.id)} style={{ marginTop: 4, marginRight: 8 }}>
                        Reconcile
                      </button>
                      <button onClick={() => deleteLdapProvider(provider.id)} style={{ marginTop: 4 }}>Delete</button>
                    </li>
                  ))}
                </ul>
              )}

              <h3 style={{ marginTop: 16 }}>Add LDAP Provider</h3>
              <form onSubmit={createLdapProvider}>
                <div>
                  <label>
                    Name:
                    <input value={ldapName} onChange={e => setLdapName(e.target.value)} required />
                  </label>
                </div>
                <div>
                  <label>
                    URL:
                    <input value={ldapUrl} onChange={e => setLdapUrl(e.target.value)} required style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    Bind DN:
                    <input value={ldapBindDn} onChange={e => setLdapBindDn(e.target.value)} style={{ width: '100%' }} />
                  </label>
                </div>
                <div>
                  <label>
                    Bind Credential:
                    <input
                      type="password"
                      value={ldapBindCredential}
                      onChange={e => setLdapBindCredential(e.target.value)}
                      style={{ width: '100%' }}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    User Search Base:
                    <input
                      value={ldapUserSearchBase}
                      onChange={e => setLdapUserSearchBase(e.target.value)}
                      style={{ width: '100%' }}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    User Search Filter:
                    <input
                      value={ldapUserSearchFilter}
                      onChange={e => setLdapUserSearchFilter(e.target.value)}
                      style={{ width: '100%' }}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Username Attribute:
                    <input value={ldapUsernameAttribute} onChange={e => setLdapUsernameAttribute(e.target.value)} />
                  </label>
                </div>
                <div>
                  <label>
                    Email Attribute:
                    <input value={ldapEmailAttribute} onChange={e => setLdapEmailAttribute(e.target.value)} />
                  </label>
                </div>
                <div>
                  <label>
                    Sync Attributes On Login:
                    <input
                      type="checkbox"
                      checked={ldapSyncAttributesOnLogin}
                      onChange={e => setLdapSyncAttributesOnLogin(e.target.checked)}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Disable Missing Users:
                    <input
                      type="checkbox"
                      checked={ldapDisableMissingUsers}
                      onChange={e => setLdapDisableMissingUsers(e.target.checked)}
                    />
                  </label>
                </div>
                <div>
                  <label>
                    Enabled:
                    <input type="checkbox" checked={ldapEnabled} onChange={e => setLdapEnabled(e.target.checked)} />
                  </label>
                </div>
                <button disabled={creatingLdapProvider || !ldapName || !ldapUrl || !accessToken.trim()}>Create</button>
              </form>
            </>
          ) : (
            <p>No realm selected</p>
          )}
        </section>
      </div>
    </div>
  )
}
