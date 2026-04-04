ALTER TABLE scim_outbound_target
  ADD COLUMN sync_on_group_change TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE scim_outbound_target
  ADD COLUMN delete_group_on_local_delete TINYINT(1) NOT NULL DEFAULT 0;
