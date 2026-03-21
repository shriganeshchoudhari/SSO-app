ALTER TABLE ldap_provider ADD (
  sync_attributes_on_login NUMBER(1) DEFAULT 1 NOT NULL,
  disable_missing_users NUMBER(1) DEFAULT 0 NOT NULL
);
