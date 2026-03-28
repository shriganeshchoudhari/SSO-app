ALTER TABLE ldap_provider ADD COLUMN IF NOT EXISTS last_reconciled_at DATETIME(6);
ALTER TABLE ldap_provider ADD COLUMN IF NOT EXISTS hard_delete_missing TINYINT(1) NOT NULL DEFAULT 0;
