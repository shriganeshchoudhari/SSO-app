ALTER TABLE client
  ADD consent_required NUMBER(1) DEFAULT 0 NOT NULL;

CREATE TABLE user_consent (
  id         VARCHAR2(36) NOT NULL PRIMARY KEY,
  realm_id   VARCHAR2(36) NOT NULL,
  user_id    VARCHAR2(36) NOT NULL,
  client_id  VARCHAR2(36) NOT NULL,
  scopes_raw VARCHAR2(1000),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_user_consent_realm_user_client UNIQUE (realm_id, user_id, client_id)
);

CREATE INDEX ix_user_consent_user ON user_consent(user_id);
CREATE INDEX ix_user_consent_client ON user_consent(client_id);

CREATE TABLE oidc_consent_state (
  id                    VARCHAR2(36) NOT NULL PRIMARY KEY,
  realm_id              VARCHAR2(36) NOT NULL,
  client_id             VARCHAR2(36) NOT NULL,
  user_id               VARCHAR2(36) NOT NULL,
  state_hash            VARCHAR2(128) NOT NULL,
  redirect_uri          VARCHAR2(4000) NOT NULL,
  original_state        VARCHAR2(1000),
  scope                 VARCHAR2(1000),
  code_challenge        VARCHAR2(255),
  code_challenge_method VARCHAR2(32),
  organization_hint     VARCHAR2(255),
  auth_source           VARCHAR2(64),
  auth_provider_alias   VARCHAR2(255),
  expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at           TIMESTAMP WITH TIME ZONE,
  created_at            TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_oidc_consent_state_hash UNIQUE (state_hash)
);

CREATE INDEX ix_oidc_consent_state_user ON oidc_consent_state(user_id);
CREATE INDEX ix_oidc_consent_state_client ON oidc_consent_state(client_id);
