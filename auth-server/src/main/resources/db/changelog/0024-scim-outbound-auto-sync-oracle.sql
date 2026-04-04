ALTER TABLE scim_outbound_target
  ADD sync_on_user_change NUMBER(1) DEFAULT 0 NOT NULL;
