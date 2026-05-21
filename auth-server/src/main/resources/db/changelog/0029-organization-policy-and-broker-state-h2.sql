ALTER TABLE organization ADD COLUMN IF NOT EXISTS require_membership_for_login BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organization ADD COLUMN IF NOT EXISTS allowed_email_domains VARCHAR(2000);
ALTER TABLE broker_login_state ADD COLUMN IF NOT EXISTS organization_hint VARCHAR(255);
ALTER TABLE saml_broker_login_state ADD COLUMN IF NOT EXISTS organization_hint VARCHAR(255);
