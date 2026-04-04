CREATE TABLE IF NOT EXISTS scim_outbound_group_link (
  id              CHAR(36) NOT NULL PRIMARY KEY,
  target_id       CHAR(36) NOT NULL,
  group_id        CHAR(36) NOT NULL,
  remote_group_id VARCHAR(255),
  created_at      DATETIME(6) NOT NULL DEFAULT NOW(6),
  last_synced_at  DATETIME(6),
  CONSTRAINT uq_scim_outbound_group_link_target_group UNIQUE (target_id, group_id)
);

CREATE INDEX ix_scim_outbound_group_link_target ON scim_outbound_group_link(target_id);
CREATE INDEX ix_scim_outbound_group_link_group ON scim_outbound_group_link(group_id);
