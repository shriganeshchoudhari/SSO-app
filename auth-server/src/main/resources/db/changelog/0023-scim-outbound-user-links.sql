ALTER TABLE scim_outbound_target
  ADD COLUMN IF NOT EXISTS delete_on_local_delete BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS scim_outbound_user_link (
  id             UUID PRIMARY KEY,
  target_id      UUID NOT NULL REFERENCES scim_outbound_target(id) ON DELETE CASCADE,
  user_id        UUID NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  remote_user_id VARCHAR(255),
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  last_synced_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_outbound_user_link_target_user UNIQUE (target_id, user_id)
);

CREATE INDEX IF NOT EXISTS ix_scim_outbound_user_link_target ON scim_outbound_user_link(target_id);
CREATE INDEX IF NOT EXISTS ix_scim_outbound_user_link_user ON scim_outbound_user_link(user_id);
