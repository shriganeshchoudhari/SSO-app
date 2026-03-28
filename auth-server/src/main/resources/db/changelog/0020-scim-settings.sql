CREATE TABLE IF NOT EXISTS scim_provisioning_settings (
  id                UUID PRIMARY KEY,
  realm_id          UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  deprovision_mode  VARCHAR(32) NOT NULL DEFAULT 'disable',
  created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_scim_settings_realm UNIQUE (realm_id)
);

CREATE INDEX IF NOT EXISTS ix_scim_settings_realm ON scim_provisioning_settings(realm_id);
