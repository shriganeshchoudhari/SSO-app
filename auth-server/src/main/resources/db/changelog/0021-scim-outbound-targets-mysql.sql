CREATE TABLE IF NOT EXISTS scim_outbound_target (
  id             CHAR(36) NOT NULL PRIMARY KEY,
  realm_id       CHAR(36) NOT NULL,
  name           VARCHAR(255) NOT NULL,
  base_url       VARCHAR(4000) NOT NULL,
  bearer_token   VARCHAR(4000),
  enabled        TINYINT(1) NOT NULL DEFAULT 1,
  created_at     DATETIME(6) NOT NULL DEFAULT NOW(6),
  last_synced_at DATETIME(6),
  CONSTRAINT uq_scim_outbound_target_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX ix_scim_outbound_target_realm ON scim_outbound_target(realm_id);
