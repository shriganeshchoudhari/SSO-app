const express = require("express");
const cookieParser = require("cookie-parser");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { DOMParser } = require("@xmldom/xmldom");

const app = express();

const role = process.env.MOCK_ROLE || "scim";
const port = Number(process.env.PORT || "8090");
const dataDir = process.env.DATA_DIR || "/data";

const authBaseUrlInternal = process.env.AUTH_BASE_URL_INTERNAL || "http://auth-server:7070";
const authBaseUrlExternal = process.env.AUTH_BASE_URL_EXTERNAL || "http://localhost:7070";
const demoRealm = process.env.OIDC_DEMO_REALM || "demo";
const demoClientId = process.env.OIDC_DEMO_CLIENT_ID || "browser-demo";
const demoRedirectUri =
  process.env.OIDC_DEMO_REDIRECT_URI || "http://localhost:8090/oidc-demo/callback";

const scimBearerToken = process.env.SCIM_BEARER_TOKEN || "mock-scim-token";
const samlEntityId = process.env.SAML_ENTITY_ID || "http://localhost:8082/simplesaml/idp";
const samlSpSloUrl =
  process.env.SAML_SP_SLO_URL ||
  "http://localhost:7070/auth/realms/demo/broker/saml/simplesamlphp/slo";
const samlDefaultUser = {
  username: process.env.SAML_USERNAME || "saml.user@example.com",
  email: process.env.SAML_EMAIL || "saml.user@example.com",
  password: process.env.SAML_PASSWORD || "Saml123!",
};
const samlCookieName = "openidentity_saml_session";

app.disable("x-powered-by");
app.use(cookieParser());
app.use(express.json({ type: ["application/json", "application/scim+json"], limit: "1mb" }));
app.use(express.urlencoded({ extended: false }));

if (role === "saml") {
  registerSamlRoutes();
} else {
  registerScimAndDemoRoutes();
}

app.listen(port, "0.0.0.0", () => {
  console.log(`openidentity-local-mocks listening on ${port} (${role})`);
});

function registerScimAndDemoRoutes() {
  ensureDir(dataDir);
  const stateFile = path.join(dataDir, "state.json");

  app.get("/health", (_req, res) => {
    res.json({ status: "UP", role: "scim" });
  });

  app.get("/", (_req, res) => {
    res.redirect("/inspect");
  });

  app.get("/inspect", (_req, res) => {
    res.type("html").send(renderInspectPage());
  });

  app.get("/inspect/state", (_req, res) => {
    res.json(loadState(stateFile));
  });

  app.get("/oidc-demo", (_req, res) => {
    res.type("html").send(renderOidcDemoPage());
  });

  app.get("/oidc-demo/callback", (_req, res) => {
    res.type("html").send(renderOidcDemoPage());
  });

  app.post("/oidc-demo/exchange", async (req, res) => {
    try {
      const { code, codeVerifier, redirectUri } = req.body || {};
      if (!code || !codeVerifier) {
        return res.status(400).json({ error: "code and codeVerifier are required" });
      }
      const body = new URLSearchParams();
      body.set("grant_type", "authorization_code");
      body.set("client_id", demoClientId);
      body.set("code", String(code));
      body.set("code_verifier", String(codeVerifier));
      body.set("redirect_uri", redirectUri || demoRedirectUri);
      const response = await fetch(
        `${authBaseUrlInternal}/auth/realms/${encodeURIComponent(demoRealm)}/protocol/openid-connect/token`,
        {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body,
        },
      );
      const payload = await response.json().catch(() => ({}));
      res.status(response.status).json(payload);
    } catch (error) {
      res.status(502).json({ error: "oidc_exchange_failed", detail: error.message });
    }
  });

  app.post("/oidc-demo/refresh", async (req, res) => {
    try {
      const { refreshToken } = req.body || {};
      if (!refreshToken) {
        return res.status(400).json({ error: "refreshToken is required" });
      }
      const body = new URLSearchParams();
      body.set("grant_type", "refresh_token");
      body.set("client_id", demoClientId);
      body.set("refresh_token", String(refreshToken));
      const response = await fetch(
        `${authBaseUrlInternal}/auth/realms/${encodeURIComponent(demoRealm)}/protocol/openid-connect/token`,
        {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body,
        },
      );
      const payload = await response.json().catch(() => ({}));
      res.status(response.status).json(payload);
    } catch (error) {
      res.status(502).json({ error: "oidc_refresh_failed", detail: error.message });
    }
  });

  app.get("/scim/v2/Users", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    const resources = filterByExternalId(Object.values(state.users), req.query.filter);
    return res.type("application/scim+json").json(listResponse(resources));
  });

  app.post("/scim/v2/Users", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    const resource = normalizeScimUser(req.body);
    const id = resource.id || crypto.randomUUID();
    state.users[id] = { ...resource, id };
    saveState(stateFile, state);
    return res.status(201).type("application/scim+json").json(state.users[id]);
  });

  app.put("/scim/v2/Users/:id", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    const id = req.params.id;
    state.users[id] = { ...normalizeScimUser(req.body), id };
    saveState(stateFile, state);
    return res.type("application/scim+json").json(state.users[id]);
  });

  app.delete("/scim/v2/Users/:id", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    if (!state.users[req.params.id]) {
      return res.status(404).end();
    }
    delete state.users[req.params.id];
    saveState(stateFile, state);
    return res.status(204).end();
  });

  app.get("/scim/v2/Groups", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    const resources = filterByExternalId(Object.values(state.groups), req.query.filter);
    return res.type("application/scim+json").json(listResponse(resources));
  });

  app.post("/scim/v2/Groups", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    const resource = normalizeScimGroup(req.body);
    const id = resource.id || crypto.randomUUID();
    state.groups[id] = { ...resource, id };
    saveState(stateFile, state);
    return res.status(201).type("application/scim+json").json(state.groups[id]);
  });

  app.put("/scim/v2/Groups/:id", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    const id = req.params.id;
    state.groups[id] = { ...normalizeScimGroup(req.body), id };
    saveState(stateFile, state);
    return res.type("application/scim+json").json(state.groups[id]);
  });

  app.delete("/scim/v2/Groups/:id", (req, res) => {
    if (!isAuthorized(req)) {
      return unauthorized(res);
    }
    const state = loadState(stateFile);
    if (!state.groups[req.params.id]) {
      return res.status(404).end();
    }
    delete state.groups[req.params.id];
    saveState(stateFile, state);
    return res.status(204).end();
  });
}

function registerSamlRoutes() {
  app.get("/health", (_req, res) => {
    res.json({ status: "UP", role: "saml" });
  });

  app.get("/", (req, res) => {
    res.type("html").send(renderSamlHome(req.cookies[samlCookieName]));
  });

  app.get("/simplesaml/sso", (req, res) => {
    const samlRequest = req.query.SAMLRequest;
    if (!samlRequest) {
      return res.status(400).send("SAMLRequest is required");
    }
    const parsed = parseSamlAuthnRequest(String(samlRequest));
    res.type("html").send(renderSamlLogin(req.query.RelayState || "", parsed, null, String(samlRequest)));
  });

  app.post("/simplesaml/login", (req, res) => {
    const { username, password, relayState, samlRequest } = req.body || {};
    if (username !== samlDefaultUser.username || password !== samlDefaultUser.password) {
      const parsed = parseSamlAuthnRequest(String(samlRequest || ""));
      return res
        .status(401)
        .type("html")
        .send(renderSamlLogin(relayState || "", parsed, "Invalid credentials", String(samlRequest || "")));
    }
    const parsed = parseSamlAuthnRequest(String(samlRequest || ""));
    const responseXml = buildSamlResponse(parsed);
    res.cookie(
      samlCookieName,
      Buffer.from(JSON.stringify({ subject: samlDefaultUser.email, username: samlDefaultUser.username, email: samlDefaultUser.email })).toString("base64url"),
      { httpOnly: false, sameSite: "lax" },
    );
    res.type("html").send(renderAutoPostForm(parsed.acsUrl, {
      SAMLResponse: Buffer.from(responseXml, "utf8").toString("base64"),
      RelayState: relayState || "",
    }));
  });

  app.get("/simplesaml/slo", (req, res) => {
    handleSamlSlo(req, res);
  });

  app.post("/simplesaml/slo", (req, res) => {
    handleSamlSlo(req, res);
  });

  app.get("/simplesaml/session", (req, res) => {
    res.type("html").send(renderSamlSession(req.cookies[samlCookieName]));
  });

  app.post("/simplesaml/initiate-logout", (req, res) => {
    const session = readSamlSession(req.cookies[samlCookieName]);
    if (!session) {
      return res.status(400).send("No SAML user session is active.");
    }
    const logoutRequest = buildSamlLogoutRequest(session.subject);
    res.clearCookie(samlCookieName);
    const redirectUrl = new URL(samlSpSloUrl);
    redirectUrl.searchParams.set("SAMLRequest", Buffer.from(logoutRequest, "utf8").toString("base64"));
    redirectUrl.searchParams.set("RelayState", "local-idp-logout");
    res.redirect(302, redirectUrl.toString());
  });
}

function handleSamlSlo(req, res) {
  if (req.query.SAMLResponse || req.body.SAMLResponse) {
    res.clearCookie(samlCookieName);
    return res.type("html").send(renderSamlLogoutComplete());
  }
  const samlRequest = String(req.query.SAMLRequest || req.body.SAMLRequest || "");
  if (!samlRequest) {
    return res.status(400).send("SAMLRequest is required");
  }
  const relayState = String(req.query.RelayState || req.body.RelayState || "");
  const parsed = parseSamlLogoutRequest(samlRequest);
  const responseXml = buildSamlLogoutResponse(parsed.requestId);
  const redirectUrl = new URL(samlSpSloUrl);
  redirectUrl.searchParams.set("SAMLResponse", Buffer.from(responseXml, "utf8").toString("base64"));
  if (relayState) {
    redirectUrl.searchParams.set("RelayState", relayState);
  }
  res.clearCookie(samlCookieName);
  return res.redirect(302, redirectUrl.toString());
}

function ensureDir(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }
}

function loadState(stateFile) {
  if (!fs.existsSync(stateFile)) {
    return { users: {}, groups: {} };
  }
  try {
    return JSON.parse(fs.readFileSync(stateFile, "utf8"));
  } catch (_error) {
    return { users: {}, groups: {} };
  }
}

function saveState(stateFile, state) {
  fs.writeFileSync(stateFile, JSON.stringify(state, null, 2));
}

function isAuthorized(req) {
  const authHeader = req.get("authorization") || "";
  return authHeader === `Bearer ${scimBearerToken}`;
}

function unauthorized(res) {
  return res.status(401).type("application/scim+json").json({
    schemas: ["urn:ietf:params:scim:api:messages:2.0:Error"],
    detail: "Unauthorized",
    status: "401",
  });
}

function filterByExternalId(resources, filter) {
  const match = String(filter || "").match(/externalId\s+eq\s+"([^"]+)"/i);
  if (!match) {
    return resources;
  }
  const externalId = match[1];
  return resources.filter((resource) => resource.externalId === externalId);
}

function listResponse(resources) {
  return {
    schemas: ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
    totalResults: resources.length,
    Resources: resources,
    startIndex: 1,
    itemsPerPage: resources.length,
  };
}

function normalizeScimUser(body) {
  const emails = Array.isArray(body?.emails) ? body.emails : [];
  return {
    schemas: body?.schemas || ["urn:ietf:params:scim:schemas:core:2.0:User"],
    externalId: body?.externalId || null,
    userName: body?.userName || null,
    displayName: body?.displayName || body?.userName || null,
    active: body?.active !== false,
    emails,
  };
}

function normalizeScimGroup(body) {
  return {
    schemas: body?.schemas || ["urn:ietf:params:scim:schemas:core:2.0:Group"],
    externalId: body?.externalId || null,
    displayName: body?.displayName || null,
    members: Array.isArray(body?.members) ? body.members : [],
  };
}

function parseSamlAuthnRequest(encodedRequest) {
  const xml = Buffer.from(encodedRequest, "base64").toString("utf8");
  const document = new DOMParser().parseFromString(xml, "text/xml");
  const root = document.documentElement;
  const issuerNode = firstElement(root, "Issuer");
  return {
    requestId: root.getAttribute("ID"),
    acsUrl: root.getAttribute("AssertionConsumerServiceURL"),
    audience: issuerNode ? issuerNode.textContent.trim() : "",
  };
}

function parseSamlLogoutRequest(encodedRequest) {
  const xml = Buffer.from(encodedRequest, "base64").toString("utf8");
  const document = new DOMParser().parseFromString(xml, "text/xml");
  const root = document.documentElement;
  return {
    requestId: root.getAttribute("ID"),
  };
}

function firstElement(root, localName) {
  const nodes = root.getElementsByTagNameNS("*", localName);
  return nodes.length > 0 ? nodes.item(0) : null;
}

function buildSamlResponse(request) {
  const responseId = "_" + crypto.randomUUID();
  const assertionId = "_" + crypto.randomUUID();
  const issueInstant = new Date().toISOString();
  const expiry = new Date(Date.now() + 5 * 60 * 1000).toISOString();
  const notBefore = new Date(Date.now() - 60 * 1000).toISOString();
  return `<?xml version="1.0" encoding="UTF-8"?>
<samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
  ID="${escapeXml(responseId)}"
  Version="2.0"
  IssueInstant="${escapeXml(issueInstant)}"
  Destination="${escapeXml(request.acsUrl)}"
  InResponseTo="${escapeXml(request.requestId)}">
  <saml:Issuer>${escapeXml(samlEntityId)}</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success" />
  </samlp:Status>
  <saml:Assertion ID="${escapeXml(assertionId)}" Version="2.0" IssueInstant="${escapeXml(issueInstant)}">
    <saml:Issuer>${escapeXml(samlEntityId)}</saml:Issuer>
    <saml:Subject>
      <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">${escapeXml(samlDefaultUser.email)}</saml:NameID>
      <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
        <saml:SubjectConfirmationData InResponseTo="${escapeXml(request.requestId)}" Recipient="${escapeXml(request.acsUrl)}" NotOnOrAfter="${escapeXml(expiry)}" />
      </saml:SubjectConfirmation>
    </saml:Subject>
    <saml:Conditions NotBefore="${escapeXml(notBefore)}" NotOnOrAfter="${escapeXml(expiry)}">
      <saml:AudienceRestriction>
        <saml:Audience>${escapeXml(request.audience)}</saml:Audience>
      </saml:AudienceRestriction>
    </saml:Conditions>
    <saml:AttributeStatement>
      <saml:Attribute Name="username">
        <saml:AttributeValue>${escapeXml(samlDefaultUser.username)}</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="email">
        <saml:AttributeValue>${escapeXml(samlDefaultUser.email)}</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="email_verified">
        <saml:AttributeValue>true</saml:AttributeValue>
      </saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>`;
}

function buildSamlLogoutResponse(inResponseTo) {
  const issueInstant = new Date().toISOString();
  return `<?xml version="1.0" encoding="UTF-8"?>
<samlp:LogoutResponse xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
  ID="_${escapeXml(crypto.randomUUID())}"
  Version="2.0"
  IssueInstant="${escapeXml(issueInstant)}"
  Destination="${escapeXml(samlSpSloUrl)}"
  InResponseTo="${escapeXml(inResponseTo)}">
  <saml:Issuer>${escapeXml(samlEntityId)}</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success" />
  </samlp:Status>
</samlp:LogoutResponse>`;
}

function buildSamlLogoutRequest(subject) {
  const issueInstant = new Date().toISOString();
  return `<?xml version="1.0" encoding="UTF-8"?>
<samlp:LogoutRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
  ID="_${escapeXml(crypto.randomUUID())}"
  Version="2.0"
  IssueInstant="${escapeXml(issueInstant)}"
  Destination="${escapeXml(samlSpSloUrl)}">
  <saml:Issuer>${escapeXml(samlEntityId)}</saml:Issuer>
  <saml:NameID>${escapeXml(subject)}</saml:NameID>
</samlp:LogoutRequest>`;
}

function readSamlSession(cookieValue) {
  if (!cookieValue) {
    return null;
  }
  try {
    return JSON.parse(Buffer.from(cookieValue, "base64url").toString("utf8"));
  } catch (_error) {
    return null;
  }
}

function escapeXml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

function renderInspectPage() {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>OpenIdentity Local Mocks</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; padding: 24px; background: #0f172a; color: #e2e8f0; }
    a { color: #93c5fd; }
    .grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
    .card { background: #111827; border: 1px solid #334155; border-radius: 12px; padding: 16px; }
    pre { white-space: pre-wrap; word-break: break-word; background: #020617; padding: 12px; border-radius: 8px; }
  </style>
</head>
<body>
  <h1>OpenIdentity Local Mock Target</h1>
  <p>
    <a href="/oidc-demo">OIDC browser demo</a>
  </p>
  <div class="grid">
    <section class="card">
      <h2>Remote Users</h2>
      <pre id="users">Loading...</pre>
    </section>
    <section class="card">
      <h2>Remote Groups</h2>
      <pre id="groups">Loading...</pre>
    </section>
  </div>
  <script>
    fetch('/inspect/state')
      .then((response) => response.json())
      .then((state) => {
        document.getElementById('users').textContent = JSON.stringify(Object.values(state.users || {}), null, 2)
        document.getElementById('groups').textContent = JSON.stringify(Object.values(state.groups || {}), null, 2)
      })
      .catch((error) => {
        document.getElementById('users').textContent = error.message
        document.getElementById('groups').textContent = error.message
      })
  </script>
</body>
</html>`;
}

function renderOidcDemoPage() {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>OpenIdentity OIDC Demo</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; padding: 24px; background: #f8fafc; color: #0f172a; }
    .card { max-width: 960px; margin: 0 auto; background: #fff; border: 1px solid #cbd5e1; border-radius: 16px; padding: 24px; }
    button { padding: 10px 14px; margin-right: 8px; border-radius: 10px; border: 1px solid #2563eb; background: #2563eb; color: #fff; cursor: pointer; }
    button.secondary { background: #fff; color: #1e293b; border-color: #94a3b8; }
    code, pre { background: #e2e8f0; border-radius: 8px; padding: 2px 6px; }
    pre { padding: 12px; white-space: pre-wrap; word-break: break-word; }
  </style>
</head>
<body>
  <div class="card">
    <h1>OIDC Browser Demo</h1>
    <p>This page exercises OpenIdentity hosted login, auth code + PKCE, and refresh tokens against the seeded <code>${demoClientId}</code> public client.</p>
    <p>
      <button id="start">Start hosted login</button>
      <button class="secondary" id="refresh">Refresh token</button>
      <button class="secondary" id="clear">Clear local state</button>
    </p>
    <p>
      <a href="${authBaseUrlExternal}/auth/realms/${encodeURIComponent(demoRealm)}/protocol/openid-connect/auth?response_type=code&client_id=${encodeURIComponent(demoClientId)}&redirect_uri=${encodeURIComponent(demoRedirectUri)}&scope=${encodeURIComponent("openid profile email")}" target="_blank" rel="noreferrer">Open hosted login directly</a>
    </p>
    <h2>Status</h2>
    <pre id="status">Waiting…</pre>
    <h2>Tokens</h2>
    <pre id="tokens">None yet.</pre>
    <h2>Decoded Access Token</h2>
    <pre id="claims">No token yet.</pre>
  </div>
  <script>
    const redirectUri = ${JSON.stringify(demoRedirectUri)}
    const authBase = ${JSON.stringify(authBaseUrlExternal)}
    const realm = ${JSON.stringify(demoRealm)}
    const clientId = ${JSON.stringify(demoClientId)}
    const storageKey = 'openidentity_oidc_demo'
    const statusEl = document.getElementById('status')
    const tokensEl = document.getElementById('tokens')
    const claimsEl = document.getElementById('claims')

    function base64Url(bytes) {
      return btoa(String.fromCharCode(...bytes)).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+$/g, '')
    }

    async function sha256(value) {
      const buffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
      return base64Url(new Uint8Array(buffer))
    }

    function randomString() {
      const bytes = new Uint8Array(32)
      crypto.getRandomValues(bytes)
      return base64Url(bytes)
    }

    function decodeJwt(token) {
      const parts = token.split('.')
      if (parts.length < 2) return null
      const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
      return JSON.parse(atob(payload.padEnd(payload.length + ((4 - payload.length % 4) % 4), '=')))
    }

    function saveState(state) {
      sessionStorage.setItem(storageKey, JSON.stringify(state))
    }

    function readState() {
      try { return JSON.parse(sessionStorage.getItem(storageKey) || '{}') } catch { return {} }
    }

    function render(data) {
      tokensEl.textContent = data ? JSON.stringify(data, null, 2) : 'None yet.'
      const claims = data?.access_token ? decodeJwt(data.access_token) : null
      claimsEl.textContent = claims ? JSON.stringify(claims, null, 2) : 'No token yet.'
    }

    async function startLogin() {
      const verifier = randomString()
      const state = randomString()
      const challenge = await sha256(verifier)
      saveState({ verifier, state })
      const url = new URL(authBase + '/auth/realms/' + encodeURIComponent(realm) + '/protocol/openid-connect/auth')
      url.searchParams.set('response_type', 'code')
      url.searchParams.set('client_id', clientId)
      url.searchParams.set('redirect_uri', redirectUri)
      url.searchParams.set('scope', 'openid profile email')
      url.searchParams.set('state', state)
      url.searchParams.set('code_challenge', challenge)
      url.searchParams.set('code_challenge_method', 'S256')
      window.location.assign(url.toString())
    }

    async function exchangeCode(code, state) {
      const local = readState()
      if (!local.state || local.state !== state) {
        statusEl.textContent = 'State mismatch. Restart the flow.'
        return
      }
      const response = await fetch('/oidc-demo/exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, codeVerifier: local.verifier, redirectUri })
      })
      const payload = await response.json()
      if (!response.ok) {
        statusEl.textContent = JSON.stringify(payload, null, 2)
        return
      }
      saveState({ ...local, tokens: payload })
      history.replaceState({}, document.title, '/oidc-demo')
      statusEl.textContent = 'Authorization code exchange succeeded.'
      render(payload)
    }

    async function refreshTokens() {
      const local = readState()
      const refreshToken = local.tokens?.refresh_token
      if (!refreshToken) {
        statusEl.textContent = 'No refresh token available.'
        return
      }
      const response = await fetch('/oidc-demo/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      })
      const payload = await response.json()
      if (!response.ok) {
        statusEl.textContent = JSON.stringify(payload, null, 2)
        return
      }
      saveState({ ...local, tokens: payload })
      statusEl.textContent = 'Refresh token flow succeeded.'
      render(payload)
    }

    function clearState() {
      sessionStorage.removeItem(storageKey)
      history.replaceState({}, document.title, '/oidc-demo')
      statusEl.textContent = 'Local state cleared.'
      render(null)
    }

    document.getElementById('start').addEventListener('click', startLogin)
    document.getElementById('refresh').addEventListener('click', refreshTokens)
    document.getElementById('clear').addEventListener('click', clearState)

    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    const state = params.get('state')
    const error = params.get('error')
    if (error) {
      statusEl.textContent = 'Authorization failed: ' + error
    } else if (code && state) {
      statusEl.textContent = 'Exchanging authorization code...'
      exchangeCode(code, state)
    } else {
      const local = readState()
      if (local.tokens) {
        statusEl.textContent = 'Ready.'
        render(local.tokens)
      } else {
        statusEl.textContent = 'No active browser session yet.'
      }
    }
  </script>
</body>
</html>`;
}

function renderSamlHome(cookieValue) {
  const session = readSamlSession(cookieValue);
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>OpenIdentity Local SAML IdP</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; padding: 24px; background: #111827; color: #f8fafc; }
    .card { max-width: 720px; margin: 0 auto; background: #1f2937; border: 1px solid #334155; border-radius: 16px; padding: 24px; }
    code { background: #0f172a; border-radius: 6px; padding: 2px 6px; }
    a { color: #93c5fd; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Local SAML IdP</h1>
    <p>This is the deterministic local SAML broker target used for OpenIdentity full-run testing.</p>
    <p>Seeded credentials: <code>${samlDefaultUser.username}</code> / <code>${samlDefaultUser.password}</code></p>
    <p>Current session: ${session ? `<code>${session.subject}</code>` : "none"}</p>
    <p><a href="/simplesaml/session">Open session console</a></p>
  </div>
</body>
</html>`;
}

function renderSamlLogin(relayState, request, error, encodedRequest) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Local SAML IdP Sign In</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: grid; place-items: center; background: #e2e8f0; }
    .card { width: min(420px, 100% - 32px); background: white; border-radius: 16px; padding: 24px; box-shadow: 0 20px 40px rgba(15, 23, 42, .15); }
    label { display: block; margin-bottom: 12px; }
    input { width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid #cbd5e1; margin-top: 4px; }
    button { width: 100%; padding: 10px 14px; border: 0; border-radius: 10px; background: #2563eb; color: white; font-weight: 700; }
    .error { color: #b91c1c; margin-bottom: 12px; }
    .meta { color: #475569; font-size: 14px; }
  </style>
</head>
<body>
  <form class="card" method="post" action="/simplesaml/login">
    <h1>Local SAML Sign In</h1>
    <p class="meta">Audience: <code>${escapeXml(request.audience || "unknown")}</code></p>
    ${error ? `<p class="error">${escapeXml(error)}</p>` : ""}
    <input type="hidden" name="relayState" value="${escapeXml(relayState)}" />
    <input type="hidden" name="samlRequest" value="${escapeXml(encodedRequest)}" />
    <label>Username<input name="username" value="${escapeXml(samlDefaultUser.username)}" /></label>
    <label>Password<input type="password" name="password" value="${escapeXml(samlDefaultUser.password)}" /></label>
    <button type="submit">Continue</button>
  </form>
</body>
</html>`;
}

function renderAutoPostForm(action, fields) {
  const inputs = Object.entries(fields)
    .map(([name, value]) => `<input type="hidden" name="${escapeXml(name)}" value="${escapeXml(value)}" />`)
    .join("\n");
  return `<!doctype html>
<html lang="en">
<head><meta charset="utf-8" /><title>Redirecting…</title></head>
<body>
  <form id="saml-post" method="post" action="${escapeXml(action)}">
    ${inputs}
  </form>
  <script>document.getElementById('saml-post').submit()</script>
</body>
</html>`;
}

function renderSamlSession(cookieValue) {
  const session = readSamlSession(cookieValue);
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Local SAML Session</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; padding: 24px; background: #f8fafc; }
    .card { max-width: 720px; margin: 0 auto; background: white; border: 1px solid #cbd5e1; border-radius: 16px; padding: 24px; }
    button { padding: 10px 14px; border-radius: 10px; border: 0; background: #2563eb; color: white; font-weight: 700; }
    code { background: #e2e8f0; border-radius: 6px; padding: 2px 6px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Local SAML Session Console</h1>
    <p>Session user: ${session ? `<code>${escapeXml(session.subject)}</code>` : "none"}</p>
    <form method="post" action="/simplesaml/initiate-logout">
      <button type="submit" ${session ? "" : "disabled"}>Start IdP-initiated logout</button>
    </form>
  </div>
</body>
</html>`;
}

function renderSamlLogoutComplete() {
  return `<!doctype html>
<html lang="en">
<head><meta charset="utf-8" /><title>SAML logout complete</title></head>
<body style="font-family: system-ui, sans-serif; padding: 24px;">
  <h1>SAML logout complete</h1>
  <p>The local SAML IdP session has been cleared.</p>
</body>
</html>`;
}
