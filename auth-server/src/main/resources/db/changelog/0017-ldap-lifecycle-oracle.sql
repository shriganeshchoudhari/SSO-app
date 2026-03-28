ALTER TABLE ldap_provider ADD last_reconciled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ldap_provider ADD hard_delete_missing NUMBER(1) DEFAULT 0 NOT NULL;
