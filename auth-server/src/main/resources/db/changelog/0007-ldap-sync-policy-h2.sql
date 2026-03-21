ALTER TABLE ldap_provider ADD sync_attributes_on_login BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE ldap_provider ADD disable_missing_users BOOLEAN DEFAULT FALSE NOT NULL;
