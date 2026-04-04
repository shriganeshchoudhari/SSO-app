ALTER TABLE scim_outbound_target
  ADD COLUMN sync_on_user_change TINYINT(1) NOT NULL DEFAULT 0;
