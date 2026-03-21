ALTER TABLE ldap_provider
  ADD COLUMN sync_attributes_on_login BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE ldap_provider
  ADD COLUMN disable_missing_users BOOLEAN NOT NULL DEFAULT FALSE;
