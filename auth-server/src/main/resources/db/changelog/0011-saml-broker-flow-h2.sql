CREATE TABLE IF NOT EXISTS saml_broker_login_state (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  provider_id UUID NOT NULL,
  client_id UUID NOT NULL,
  relay_state_hash VARCHAR(128) NOT NULL,
  redirect_uri VARCHAR(4000) NOT NULL,
  original_state VARCHAR(1000),
  scope VARCHAR(1000),
  code_challenge VARCHAR(255),
  code_challenge_method VARCHAR(32),
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_saml_broker_login_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_saml_broker_login_provider FOREIGN KEY (provider_id) REFERENCES saml_identity_provider(id) ON DELETE CASCADE,
  CONSTRAINT fk_saml_broker_login_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
