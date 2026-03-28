CREATE TABLE scim_outbound_target (
  id             VARCHAR2(36) NOT NULL PRIMARY KEY,
  realm_id       VARCHAR2(36) NOT NULL,
  name           VARCHAR2(255) NOT NULL,
  base_url       VARCHAR2(4000) NOT NULL,
  bearer_token   VARCHAR2(4000),
  enabled        NUMBER(1) DEFAULT 1 NOT NULL,
  created_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_synced_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_outbound_target_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX ix_scim_outbound_target_realm ON scim_outbound_target(realm_id);
