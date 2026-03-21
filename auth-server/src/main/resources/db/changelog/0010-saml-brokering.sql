CREATE TABLE IF NOT EXISTS saml_identity_provider (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  alias TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  sso_url TEXT NOT NULL,
  slo_url TEXT,
  x509_certificate TEXT,
  name_id_format TEXT,
  sync_attributes_on_login BOOLEAN NOT NULL DEFAULT TRUE,
  want_authn_requests_signed BOOLEAN NOT NULL DEFAULT FALSE,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(realm_id, alias)
);
