ALTER TABLE client
  ADD COLUMN consent_required TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS user_consent (
  id         CHAR(36) NOT NULL PRIMARY KEY,
  realm_id   CHAR(36) NOT NULL,
  user_id    CHAR(36) NOT NULL,
  client_id  CHAR(36) NOT NULL,
  scopes_raw VARCHAR(1000),
  created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
  updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
  CONSTRAINT uq_user_consent_realm_user_client UNIQUE (realm_id, user_id, client_id)
);

CREATE INDEX ix_user_consent_user ON user_consent(user_id);
CREATE INDEX ix_user_consent_client ON user_consent(client_id);

CREATE TABLE IF NOT EXISTS oidc_consent_state (
  id                    CHAR(36) NOT NULL PRIMARY KEY,
  realm_id              CHAR(36) NOT NULL,
  client_id             CHAR(36) NOT NULL,
  user_id               CHAR(36) NOT NULL,
  state_hash            VARCHAR(128) NOT NULL,
  redirect_uri          VARCHAR(4000) NOT NULL,
  original_state        VARCHAR(1000),
  scope                 VARCHAR(1000),
  code_challenge        VARCHAR(255),
  code_challenge_method VARCHAR(32),
  organization_hint     VARCHAR(255),
  auth_source           VARCHAR(64),
  auth_provider_alias   VARCHAR(255),
  expires_at            DATETIME(6) NOT NULL,
  consumed_at           DATETIME(6),
  created_at            DATETIME(6) NOT NULL DEFAULT NOW(6),
  CONSTRAINT uq_oidc_consent_state_hash UNIQUE (state_hash)
);

CREATE INDEX ix_oidc_consent_state_user ON oidc_consent_state(user_id);
CREATE INDEX ix_oidc_consent_state_client ON oidc_consent_state(client_id);
