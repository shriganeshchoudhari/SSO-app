ALTER TABLE client
  ADD COLUMN IF NOT EXISTS consent_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS user_consent (
  id         UUID PRIMARY KEY,
  realm_id   UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  user_id    UUID NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  client_id  UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
  scopes_raw VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_user_consent_realm_user_client UNIQUE (realm_id, user_id, client_id)
);

CREATE INDEX IF NOT EXISTS ix_user_consent_user ON user_consent(user_id);
CREATE INDEX IF NOT EXISTS ix_user_consent_client ON user_consent(client_id);

CREATE TABLE IF NOT EXISTS oidc_consent_state (
  id                    UUID PRIMARY KEY,
  realm_id              UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  client_id             UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
  user_id               UUID NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  state_hash            VARCHAR(128) NOT NULL,
  redirect_uri          VARCHAR(4000) NOT NULL,
  original_state        VARCHAR(1000),
  scope                 VARCHAR(1000),
  code_challenge        VARCHAR(255),
  code_challenge_method VARCHAR(32),
  organization_hint     VARCHAR(255),
  auth_source           VARCHAR(64),
  auth_provider_alias   VARCHAR(255),
  expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at           TIMESTAMP WITH TIME ZONE,
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_oidc_consent_state_hash UNIQUE (state_hash)
);

CREATE INDEX IF NOT EXISTS ix_oidc_consent_state_user ON oidc_consent_state(user_id);
CREATE INDEX IF NOT EXISTS ix_oidc_consent_state_client ON oidc_consent_state(client_id);
