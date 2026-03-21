ALTER TABLE ldap_provider ADD COLUMN sync_attributes_on_login TINYINT(1) DEFAULT 1 NOT NULL;
ALTER TABLE ldap_provider ADD COLUMN disable_missing_users TINYINT(1) DEFAULT 0 NOT NULL;
