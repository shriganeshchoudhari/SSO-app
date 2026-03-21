ALTER TABLE iam_user ADD federation_external_id VARCHAR2(255);

CREATE TABLE broker_login_state (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  provider_id CHAR(36) NOT NULL,
  client_id CHAR(36) NOT NULL,
  state_hash VARCHAR2(128) NOT NULL,
  redirect_uri VARCHAR2(4000) NOT NULL,
  original_state VARCHAR2(1000),
  scope VARCHAR2(1000),
  code_challenge VARCHAR2(255),
  code_challenge_method VARCHAR2(32),
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_broker_login_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_broker_login_provider FOREIGN KEY (provider_id) REFERENCES oidc_identity_provider(id) ON DELETE CASCADE,
  CONSTRAINT fk_broker_login_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
