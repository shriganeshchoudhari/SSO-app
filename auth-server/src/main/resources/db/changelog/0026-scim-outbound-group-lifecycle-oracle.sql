ALTER TABLE scim_outbound_target
  ADD sync_on_group_change NUMBER(1) DEFAULT 0 NOT NULL;

ALTER TABLE scim_outbound_target
  ADD delete_group_on_local_delete NUMBER(1) DEFAULT 0 NOT NULL;
