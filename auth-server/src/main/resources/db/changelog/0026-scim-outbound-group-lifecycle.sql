ALTER TABLE scim_outbound_target
  ADD COLUMN IF NOT EXISTS sync_on_group_change BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE scim_outbound_target
  ADD COLUMN IF NOT EXISTS delete_group_on_local_delete BOOLEAN NOT NULL DEFAULT FALSE;
