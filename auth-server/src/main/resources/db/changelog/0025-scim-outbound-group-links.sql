CREATE TABLE IF NOT EXISTS scim_outbound_group_link (
  id              UUID PRIMARY KEY,
  target_id       UUID NOT NULL REFERENCES scim_outbound_target(id) ON DELETE CASCADE,
  group_id        UUID NOT NULL REFERENCES scim_group(id) ON DELETE CASCADE,
  remote_group_id VARCHAR(255),
  created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  last_synced_at  TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_outbound_group_link_target_group UNIQUE (target_id, group_id)
);

CREATE INDEX IF NOT EXISTS ix_scim_outbound_group_link_target ON scim_outbound_group_link(target_id);
CREATE INDEX IF NOT EXISTS ix_scim_outbound_group_link_group ON scim_outbound_group_link(group_id);
