CREATE TABLE scim_provisioning_settings (
  id                VARCHAR2(36) NOT NULL PRIMARY KEY,
  realm_id          VARCHAR2(36) NOT NULL,
  deprovision_mode  VARCHAR2(32) DEFAULT 'disable' NOT NULL,
  created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_scim_settings_realm UNIQUE (realm_id)
);

CREATE INDEX ix_scim_settings_realm ON scim_provisioning_settings(realm_id);
