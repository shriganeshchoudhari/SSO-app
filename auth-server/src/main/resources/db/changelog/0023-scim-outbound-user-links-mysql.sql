ALTER TABLE scim_outbound_target
  ADD COLUMN delete_on_local_delete TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS scim_outbound_user_link (
  id             CHAR(36) NOT NULL PRIMARY KEY,
  target_id      CHAR(36) NOT NULL,
  user_id        CHAR(36) NOT NULL,
  remote_user_id VARCHAR(255),
  created_at     DATETIME(6) NOT NULL DEFAULT NOW(6),
  last_synced_at DATETIME(6),
  CONSTRAINT uq_scim_outbound_user_link_target_user UNIQUE (target_id, user_id)
);

CREATE INDEX ix_scim_outbound_user_link_target ON scim_outbound_user_link(target_id);
CREATE INDEX ix_scim_outbound_user_link_user ON scim_outbound_user_link(user_id);
