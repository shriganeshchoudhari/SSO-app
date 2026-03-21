CREATE TABLE IF NOT EXISTS ldap_provider (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  name VARCHAR(255) NOT NULL,
  url VARCHAR(1024) NOT NULL,
  bind_dn VARCHAR(1024),
  bind_credential VARCHAR(4000),
  user_search_base VARCHAR(1024),
  user_search_filter VARCHAR(1024),
  username_attribute VARCHAR(255),
  email_attribute VARCHAR(255),
  enabled TINYINT(1) DEFAULT 1 NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ldap_provider_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_ldap_provider UNIQUE (realm_id, name)
);
