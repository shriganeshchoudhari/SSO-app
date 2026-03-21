CREATE TABLE IF NOT EXISTS saml_broker_logout_state (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  provider_id UUID NOT NULL,
  relay_state_hash VARCHAR(128) NOT NULL,
  logout_request_id VARCHAR(255) NOT NULL,
  session_id UUID NOT NULL,
  post_logout_redirect_uri VARCHAR(4000),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_saml_logout_state_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_saml_logout_state_provider FOREIGN KEY (provider_id) REFERENCES saml_identity_provider(id) ON DELETE CASCADE
);
