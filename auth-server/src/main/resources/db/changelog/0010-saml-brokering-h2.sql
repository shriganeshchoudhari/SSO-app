CREATE TABLE IF NOT EXISTS saml_identity_provider (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  alias VARCHAR(255) NOT NULL,
  entity_id VARCHAR(1024) NOT NULL,
  sso_url VARCHAR(1024) NOT NULL,
  slo_url VARCHAR(1024),
  x509_certificate VARCHAR(8000),
  name_id_format VARCHAR(255),
  sync_attributes_on_login BOOLEAN DEFAULT TRUE NOT NULL,
  want_authn_requests_signed BOOLEAN DEFAULT FALSE NOT NULL,
  enabled BOOLEAN DEFAULT TRUE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_saml_identity_provider_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_saml_identity_provider UNIQUE (realm_id, alias)
);
