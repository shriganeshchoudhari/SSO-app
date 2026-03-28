CREATE TABLE IF NOT EXISTS scim_provisioning_settings (
  id                CHAR(36) NOT NULL PRIMARY KEY,
  realm_id          CHAR(36) NOT NULL,
  deprovision_mode  VARCHAR(32) NOT NULL DEFAULT 'disable',
  created_at        DATETIME(6) NOT NULL DEFAULT NOW(6),
  updated_at        DATETIME(6) NOT NULL DEFAULT NOW(6),
  CONSTRAINT uq_scim_settings_realm UNIQUE (realm_id)
);

CREATE INDEX ix_scim_settings_realm ON scim_provisioning_settings(realm_id);
