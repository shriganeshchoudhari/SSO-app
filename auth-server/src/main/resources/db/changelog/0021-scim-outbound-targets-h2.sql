CREATE TABLE IF NOT EXISTS scim_outbound_target (
  id             UUID PRIMARY KEY,
  realm_id       UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  name           VARCHAR(255) NOT NULL,
  base_url       VARCHAR(4000) NOT NULL,
  bearer_token   VARCHAR(4000),
  enabled        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  last_synced_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_outbound_target_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX IF NOT EXISTS ix_scim_outbound_target_realm ON scim_outbound_target(realm_id);
