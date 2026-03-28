CREATE TABLE IF NOT EXISTS scim_group_role_mapping (
  id           UUID                     PRIMARY KEY,
  group_id     UUID                     NOT NULL REFERENCES scim_group(id) ON DELETE CASCADE,
  role_id      UUID                     NOT NULL REFERENCES role(id) ON DELETE CASCADE,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_scim_group_role_mapping UNIQUE (group_id, role_id)
);

CREATE INDEX IF NOT EXISTS ix_scim_group_role_mapping_group ON scim_group_role_mapping(group_id);
CREATE INDEX IF NOT EXISTS ix_scim_group_role_mapping_role  ON scim_group_role_mapping(role_id);
