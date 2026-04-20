const authBaseInternal = process.env.AUTH_BASE_URL_INTERNAL || "http://auth-server:7070";
const authBaseExternal = process.env.AUTH_BASE_URL_EXTERNAL || "http://localhost:7070";
const bootstrapToken =
  process.env.OPENIDENTITY_ADMIN_BOOTSTRAP_TOKEN || "local-bootstrap-token";

const realmSeed = {
  name: "demo",
  displayName: "OpenIdentity Demo Realm",
  enabled: true,
  mfaPolicy: "optional",
};

const users = {
  admin: {
    username: "admin",
    email: "admin@example.com",
    password: "Admin123!",
  },
  account: {
    username: "demo.user",
    email: "demo.user@example.com",
    password: "User123!",
  },
};

const clients = {
  account: {
    clientId: "account",
    protocol: "oidc",
    publicClient: true,
    redirectUris: [],
    grantTypes: ["password", "refresh_token"],
  },
  adminCli: {
    clientId: "admin-cli",
    protocol: "oidc",
    publicClient: true,
    redirectUris: [],
    grantTypes: ["password", "refresh_token"],
  },
  browserDemo: {
    clientId: "browser-demo",
    protocol: "oidc",
    publicClient: true,
    redirectUris: ["http://localhost:8090/oidc-demo/callback"],
    grantTypes: ["authorization_code", "refresh_token"],
  },
};

const ldapProvider = {
  name: "openldap-local",
  url: "ldap://openldap:389",
  bindDn: "cn=admin,dc=example,dc=org",
  bindCredential: "admin",
  userSearchBase: "ou=people,dc=example,dc=org",
  userSearchFilter: "(uid={0})",
  usernameAttribute: "uid",
  emailAttribute: "mail",
  syncAttributesOnLogin: true,
  disableMissingUsers: false,
  enabled: true,
};

const oidcProvider = {
  alias: "dex",
  issuerUrl: "http://localhost:5556/dex",
  authorizationUrl: "http://localhost:5556/dex/auth",
  tokenUrl: "http://dex:5556/dex/token",
  userInfoUrl: "http://dex:5556/dex/userinfo",
  jwksUrl: "http://dex:5556/dex/keys",
  clientId: "openidentity-broker",
  clientSecret: "openidentity-broker-secret",
  scopes: ["openid", "profile", "email"],
  usernameClaim: "preferred_username",
  emailClaim: "email",
  syncAttributesOnLogin: true,
  enabled: true,
};

const samlProvider = {
  alias: "simplesamlphp",
  entityId: "http://localhost:8082/simplesaml/idp",
  ssoUrl: "http://localhost:8082/simplesaml/sso",
  sloUrl: "http://localhost:8082/simplesaml/slo",
  x509Certificate: "",
  nameIdFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
  syncAttributesOnLogin: true,
  wantAuthnRequestsSigned: false,
  enabled: true,
};

const outboundTarget = {
  name: "mock-scim-target",
  baseUrl: "http://mock-scim-target:8090/scim/v2",
  bearerToken: "mock-scim-token",
  enabled: true,
  syncOnUserChange: true,
  syncOnGroupChange: true,
  deleteOnLocalDelete: true,
  deleteGroupOnLocalDelete: true,
};

const scimSeedGroup = {
  displayName: "demo-group",
  externalId: "demo-group",
};

await waitForReadiness();

const realm = await ensureRealm();
const adminRole = await ensureRole(realm.id, "admin");
const adminUser = await ensureUser(realm.id, users.admin);
await setPassword(realm.id, adminUser.id, users.admin.password);
const accountUser = await ensureUser(realm.id, users.account);
await setPassword(realm.id, accountUser.id, users.account.password);
await ensureRoleAssignment(realm.id, adminUser.id, adminRole.id);

await ensureClient(realm.id, clients.account);
await ensureClient(realm.id, clients.adminCli);
await ensureClient(realm.id, clients.browserDemo);

await ensureLdapProvider(realm.id, ldapProvider);
await ensureOidcProvider(realm.id, oidcProvider);
await ensureSamlProvider(realm.id, samlProvider);

await ensureScimGroup(scimSeedGroup);
const scimTarget = await ensureOutboundTarget(realm.id, outboundTarget);
await syncOutboundUsers(realm.id, scimTarget.id);
await syncOutboundGroups(realm.id, scimTarget.id);

printSummary();

async function waitForReadiness() {
  const deadline = Date.now() + 180_000;
  let lastError = "auth-server readiness check did not start";
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${authBaseInternal}/q/health/ready`);
      if (response.ok) {
        return;
      }
      lastError = `readiness status ${response.status}`;
    } catch (error) {
      lastError = error.message;
    }
    await sleep(3000);
  }
  throw new Error(`Timed out waiting for auth-server readiness: ${lastError}`);
}

async function ensureRealm() {
  const realms = await api("/admin/realms");
  const existing = realms.find((realm) => realm.name === realmSeed.name);
  if (existing) {
    await api(`/admin/realms/${existing.id}`, {
      method: "PUT",
      body: {
        displayName: realmSeed.displayName,
        enabled: realmSeed.enabled,
        mfaPolicy: realmSeed.mfaPolicy,
      },
    });
    return { ...existing, ...realmSeed };
  }
  return api("/admin/realms", {
    method: "POST",
    body: realmSeed,
  });
}

async function ensureUser(realmId, seed) {
  const users = await api(`/admin/realms/${realmId}/users`);
  const existing = users.find((user) => user.username === seed.username);
  if (existing) {
    await api(`/admin/realms/${realmId}/users/${existing.id}`, {
      method: "PUT",
      body: { email: seed.email, enabled: true },
    });
    return { ...existing, email: seed.email, enabled: true };
  }
  return api(`/admin/realms/${realmId}/users`, {
    method: "POST",
    body: {
      username: seed.username,
      email: seed.email,
      enabled: true,
    },
  });
}

async function setPassword(realmId, userId, password) {
  await api(`/admin/realms/${realmId}/users/${userId}/credentials/password`, {
    method: "POST",
    body: { password },
    parseJson: false,
  });
}

async function ensureRole(realmId, name) {
  const roles = await api(`/admin/realms/${realmId}/roles`);
  const existing = roles.find((role) => role.name === name && !role.clientId);
  if (existing) {
    return existing;
  }
  return api(`/admin/realms/${realmId}/roles`, {
    method: "POST",
    body: { name },
  });
}

async function ensureRoleAssignment(realmId, userId, roleId) {
  const roles = await api(`/admin/realms/${realmId}/users/${userId}/roles`);
  const alreadyAssigned = roles.some((role) => role.id === roleId);
  if (alreadyAssigned) {
    return;
  }
  await api(`/admin/realms/${realmId}/users/${userId}/roles/${roleId}`, {
    method: "POST",
    parseJson: false,
  });
}

async function ensureClient(realmId, seed) {
  const clients = await api(`/admin/realms/${realmId}/clients`);
  const existing = clients.find((client) => client.clientId === seed.clientId);
  if (existing) {
    await api(`/admin/realms/${realmId}/clients/${existing.id}`, {
      method: "PUT",
      body: {
        publicClient: seed.publicClient,
        redirectUris: seed.redirectUris,
        grantTypes: seed.grantTypes,
      },
      parseJson: false,
    });
    return existing;
  }
  return api(`/admin/realms/${realmId}/clients`, {
    method: "POST",
    body: seed,
  });
}

async function ensureLdapProvider(realmId, seed) {
  const providers = await api(`/admin/realms/${realmId}/federation/ldap`);
  const existing = providers.find((provider) => provider.name === seed.name);
  if (existing) {
    await api(`/admin/realms/${realmId}/federation/ldap/${existing.id}`, {
      method: "PUT",
      body: seed,
      parseJson: false,
    });
    return existing;
  }
  return api(`/admin/realms/${realmId}/federation/ldap`, {
    method: "POST",
    body: seed,
  });
}

async function ensureOidcProvider(realmId, seed) {
  const providers = await api(`/admin/realms/${realmId}/brokering/oidc`);
  const existing = providers.find((provider) => provider.alias === seed.alias);
  if (existing) {
    await api(`/admin/realms/${realmId}/brokering/oidc/${existing.id}`, {
      method: "PUT",
      body: seed,
      parseJson: false,
    });
    return existing;
  }
  return api(`/admin/realms/${realmId}/brokering/oidc`, {
    method: "POST",
    body: seed,
  });
}

async function ensureSamlProvider(realmId, seed) {
  const providers = await api(`/admin/realms/${realmId}/brokering/saml`);
  const existing = providers.find((provider) => provider.alias === seed.alias);
  if (existing) {
    await api(`/admin/realms/${realmId}/brokering/saml/${existing.id}`, {
      method: "PUT",
      body: seed,
      parseJson: false,
    });
    return existing;
  }
  return api(`/admin/realms/${realmId}/brokering/saml`, {
    method: "POST",
    body: seed,
  });
}

async function ensureOutboundTarget(realmId, seed) {
  const targets = await api(`/admin/realms/${realmId}/scim/outbound-targets`);
  const existing = targets.find((target) => target.name === seed.name);
  if (existing) {
    await api(`/admin/realms/${realmId}/scim/outbound-targets/${existing.id}`, {
      method: "PUT",
      body: seed,
      parseJson: false,
    });
    return existing;
  }
  return api(`/admin/realms/${realmId}/scim/outbound-targets`, {
    method: "POST",
    body: seed,
  });
}

async function ensureScimGroup(seed) {
  const groups = await scim(
    `/scim/v2/realms/${encodeURIComponent(realmSeed.name)}/Groups?filter=${encodeURIComponent(
      `displayName eq "${seed.displayName}"`,
    )}`,
  );
  if (groups.totalResults > 0) {
    return groups.Resources[0];
  }
  const group = await scim(`/scim/v2/realms/${encodeURIComponent(realmSeed.name)}/Groups`, {
    method: "POST",
    body: {
      schemas: ["urn:ietf:params:scim:schemas:core:2.0:Group"],
      displayName: seed.displayName,
      externalId: seed.externalId,
      members: [],
    },
  });
  return group;
}

async function syncOutboundUsers(realmId, targetId) {
  await api(`/admin/realms/${realmId}/scim/outbound-targets/${targetId}/sync-users`, {
    method: "POST",
    parseJson: false,
  });
}

async function syncOutboundGroups(realmId, targetId) {
  await api(`/admin/realms/${realmId}/scim/outbound-targets/${targetId}/sync-groups`, {
    method: "POST",
    parseJson: false,
  });
}

async function api(path, options = {}) {
  return request(authBaseInternal, path, {
    ...options,
    headers: {
      Authorization: `Bearer ${bootstrapToken}`,
      ...(options.headers || {}),
    },
  });
}

async function scim(path, options = {}) {
  return request(authBaseInternal, path, options);
}

async function request(baseUrl, resourcePath, options = {}) {
  const method = options.method || "GET";
  const parseJson = options.parseJson !== false;
  const headers = new Headers(options.headers || {});
  let body;
  if (options.body !== undefined) {
    body = JSON.stringify(options.body);
    headers.set("Content-Type", headers.get("Content-Type") || "application/json");
    headers.set("Accept", headers.get("Accept") || "application/json");
  }
  const response = await fetch(`${baseUrl}${resourcePath}`, {
    method,
    headers,
    body,
  });
  if (response.status === 204 || !parseJson) {
    if (!response.ok) {
      throw new Error(`${method} ${resourcePath} failed with ${response.status}`);
    }
    return null;
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(`${method} ${resourcePath} failed with ${response.status}: ${text}`);
  }
  return data;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function printSummary() {
  const adminTokenCommand = `$body='grant_type=password&client_id=admin-cli&username=${users.admin.username}&password=${users.admin.password}'; (Invoke-RestMethod -Method Post -Uri '${authBaseExternal}/auth/realms/${realmSeed.name}/protocol/openid-connect/token' -ContentType 'application/x-www-form-urlencoded' -Body $body).access_token`;
  const lines = [
    "",
    "OpenIdentity local bootstrap complete.",
    "",
    "Seeded URLs",
    `- Admin UI: http://localhost:3000`,
    `- Account UI: http://localhost:3001`,
    `- OIDC browser demo: http://localhost:8090/oidc-demo`,
    `- SCIM inspection: http://localhost:8090/inspect`,
    `- Dex discovery: http://localhost:5556/dex/.well-known/openid-configuration`,
    `- Local SAML IdP: http://localhost:8082`,
    "",
    "Seeded credentials",
    `- Bootstrap token: ${bootstrapToken}`,
    `- Realm admin: ${users.admin.username} / ${users.admin.password}`,
    `- Account user: ${users.account.username} / ${users.account.password}`,
    `- LDAP user: ldap.user / Ldap123!`,
    `- Dex user: broker.user@example.com / Broker123!`,
    `- SAML user: saml.user@example.com / Saml123!`,
    "",
    "Admin token helper (PowerShell)",
    adminTokenCommand,
    "",
    "Bootstrap note",
    "- Admin APIs accept the bootstrap token directly, but the admin UI expects a bearer token. Use the helper command above to mint one from the seeded admin account.",
    "",
  ];
  console.log(lines.join("\n"));
}
