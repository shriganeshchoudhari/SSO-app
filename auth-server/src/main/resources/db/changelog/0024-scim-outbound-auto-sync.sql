ALTER TABLE scim_outbound_target
  ADD COLUMN IF NOT EXISTS sync_on_user_change BOOLEAN NOT NULL DEFAULT FALSE;
