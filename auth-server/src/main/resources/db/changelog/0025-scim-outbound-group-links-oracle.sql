CREATE TABLE scim_outbound_group_link (
  id              VARCHAR2(36) NOT NULL PRIMARY KEY,
  target_id       VARCHAR2(36) NOT NULL,
  group_id        VARCHAR2(36) NOT NULL,
  remote_group_id VARCHAR2(255),
  created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_synced_at  TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_outbound_group_link_target_group UNIQUE (target_id, group_id)
);

CREATE INDEX ix_scim_outbound_group_link_target ON scim_outbound_group_link(target_id);
CREATE INDEX ix_scim_outbound_group_link_group ON scim_outbound_group_link(group_id);
