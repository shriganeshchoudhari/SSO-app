ALTER TABLE scim_outbound_target
  ADD delete_on_local_delete NUMBER(1) DEFAULT 0 NOT NULL;

CREATE TABLE scim_outbound_user_link (
  id             VARCHAR2(36) NOT NULL PRIMARY KEY,
  target_id      VARCHAR2(36) NOT NULL,
  user_id        VARCHAR2(36) NOT NULL,
  remote_user_id VARCHAR2(255),
  created_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_synced_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_outbound_user_link_target_user UNIQUE (target_id, user_id)
);

CREATE INDEX ix_scim_outbound_user_link_target ON scim_outbound_user_link(target_id);
CREATE INDEX ix_scim_outbound_user_link_user ON scim_outbound_user_link(user_id);
