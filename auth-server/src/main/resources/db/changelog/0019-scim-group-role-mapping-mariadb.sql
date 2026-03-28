CREATE TABLE IF NOT EXISTS scim_group_role_mapping (
  id           CHAR(36)    NOT NULL PRIMARY KEY,
  group_id     CHAR(36)    NOT NULL,
  role_id      CHAR(36)    NOT NULL,
  created_at   DATETIME(6) NOT NULL DEFAULT NOW(6),
  CONSTRAINT uq_scim_group_role_mapping UNIQUE (group_id, role_id)
);

CREATE INDEX ix_scim_group_role_mapping_group ON scim_group_role_mapping(group_id);
CREATE INDEX ix_scim_group_role_mapping_role  ON scim_group_role_mapping(role_id);
