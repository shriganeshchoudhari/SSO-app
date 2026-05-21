ALTER TABLE organization ADD require_membership_for_login NUMBER(1,0) DEFAULT 0 NOT NULL;
ALTER TABLE organization ADD allowed_email_domains VARCHAR2(2000);
ALTER TABLE broker_login_state ADD organization_hint VARCHAR2(255);
ALTER TABLE saml_broker_login_state ADD organization_hint VARCHAR2(255);
