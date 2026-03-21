CREATE TABLE IF NOT EXISTS saml_broker_login_state (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  provider_id UUID NOT NULL REFERENCES saml_identity_provider(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
  relay_state_hash TEXT NOT NULL,
  redirect_uri TEXT NOT NULL,
  original_state TEXT,
  scope TEXT,
  code_challenge TEXT,
  code_challenge_method TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);
