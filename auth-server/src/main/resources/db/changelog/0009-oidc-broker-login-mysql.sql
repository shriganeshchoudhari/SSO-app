ALTER TABLE iam_user ADD federation_external_id VARCHAR(255);

CREATE TABLE IF NOT EXISTS broker_login_state (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  provider_id CHAR(36) NOT NULL,
  client_id CHAR(36) NOT NULL,
  state_hash VARCHAR(128) NOT NULL,
  redirect_uri VARCHAR(4000) NOT NULL,
  original_state VARCHAR(1000),
  scope VARCHAR(1000),
  code_challenge VARCHAR(255),
  code_challenge_method VARCHAR(32),
  expires_at DATETIME NOT NULL,
  consumed_at DATETIME,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_broker_login_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_broker_login_provider FOREIGN KEY (provider_id) REFERENCES oidc_identity_provider(id) ON DELETE CASCADE,
  CONSTRAINT fk_broker_login_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
