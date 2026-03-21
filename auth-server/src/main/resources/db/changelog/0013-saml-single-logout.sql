CREATE TABLE IF NOT EXISTS saml_broker_logout_state (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  provider_id UUID NOT NULL REFERENCES saml_identity_provider(id) ON DELETE CASCADE,
  relay_state_hash TEXT NOT NULL,
  logout_request_id TEXT NOT NULL,
  session_id UUID NOT NULL,
  post_logout_redirect_uri TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);
