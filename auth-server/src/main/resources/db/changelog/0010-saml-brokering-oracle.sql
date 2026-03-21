CREATE TABLE saml_identity_provider (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  alias VARCHAR2(255) NOT NULL,
  entity_id VARCHAR2(1024) NOT NULL,
  sso_url VARCHAR2(1024) NOT NULL,
  slo_url VARCHAR2(1024),
  x509_certificate VARCHAR2(8000),
  name_id_format VARCHAR2(255),
  sync_attributes_on_login NUMBER(1) DEFAULT 1 NOT NULL,
  want_authn_requests_signed NUMBER(1) DEFAULT 0 NOT NULL,
  enabled NUMBER(1) DEFAULT 1 NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_saml_identity_provider_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_saml_identity_provider UNIQUE (realm_id, alias)
);
