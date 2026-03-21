CREATE TABLE saml_broker_logout_state (
  id RAW(16) PRIMARY KEY,
  realm_id RAW(16) NOT NULL,
  provider_id RAW(16) NOT NULL,
  relay_state_hash VARCHAR2(128 CHAR) NOT NULL,
  logout_request_id VARCHAR2(255 CHAR) NOT NULL,
  session_id RAW(16) NOT NULL,
  post_logout_redirect_uri VARCHAR2(4000 CHAR),
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL
);
ALTER TABLE saml_broker_logout_state
  ADD CONSTRAINT fk_saml_logout_state_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE;
ALTER TABLE saml_broker_logout_state
  ADD CONSTRAINT fk_saml_logout_state_provider FOREIGN KEY (provider_id) REFERENCES saml_identity_provider(id) ON DELETE CASCADE;
