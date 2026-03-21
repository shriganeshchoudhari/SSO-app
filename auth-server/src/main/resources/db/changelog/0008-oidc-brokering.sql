CREATE TABLE IF NOT EXISTS oidc_identity_provider (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  alias TEXT NOT NULL,
  issuer_url TEXT NOT NULL,
  authorization_url TEXT,
  token_url TEXT,
  user_info_url TEXT,
  jwks_url TEXT,
  client_id TEXT NOT NULL,
  client_secret TEXT,
  scopes_raw TEXT,
  username_claim TEXT,
  email_claim TEXT,
  sync_attributes_on_login BOOLEAN NOT NULL DEFAULT TRUE,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(realm_id, alias)
);
