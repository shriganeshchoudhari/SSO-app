ALTER TABLE iam_user
  ADD COLUMN federation_external_id TEXT;

CREATE TABLE IF NOT EXISTS broker_login_state (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  provider_id UUID NOT NULL REFERENCES oidc_identity_provider(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
  state_hash TEXT NOT NULL,
  redirect_uri TEXT NOT NULL,
  original_state TEXT,
  scope TEXT,
  code_challenge TEXT,
  code_challenge_method TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);
