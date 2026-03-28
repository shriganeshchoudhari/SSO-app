CREATE TABLE scim_group_role_mapping (
  id           VARCHAR2(36)               NOT NULL PRIMARY KEY,
  group_id     VARCHAR2(36)               NOT NULL,
  role_id      VARCHAR2(36)               NOT NULL,
  created_at   TIMESTAMP WITH TIME ZONE   DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_scim_group_role_mapping UNIQUE (group_id, role_id)
);

CREATE INDEX ix_scim_group_role_mapping_group ON scim_group_role_mapping(group_id);
CREATE INDEX ix_scim_group_role_mapping_role  ON scim_group_role_mapping(role_id);
