CREATE TABLE IF NOT EXISTS scim_user (
  id               UUID          PRIMARY KEY,
  realm_id         UUID          NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  external_id      VARCHAR(255),
  user_name        VARCHAR(255)  NOT NULL,
  display_name     VARCHAR(255),
  given_name       VARCHAR(255),
  family_name      VARCHAR(255),
  email            VARCHAR(255),
  active           BOOLEAN       NOT NULL DEFAULT TRUE,
  provisioned_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  last_synced_at   TIMESTAMP WITH TIME ZONE,
  iam_user_id      UUID          REFERENCES iam_user(id) ON DELETE SET NULL,
  CONSTRAINT uq_scim_user_realm_username UNIQUE (realm_id, user_name)
);
CREATE INDEX IF NOT EXISTS ix_scim_user_realm    ON scim_user(realm_id);
CREATE INDEX IF NOT EXISTS ix_scim_user_ext_id   ON scim_user(external_id);
CREATE INDEX IF NOT EXISTS ix_scim_user_iam_user ON scim_user(iam_user_id);

CREATE TABLE IF NOT EXISTS scim_group (
  id               UUID          PRIMARY KEY,
  realm_id         UUID          NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  external_id      VARCHAR(255),
  display_name     VARCHAR(255)  NOT NULL,
  provisioned_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  last_synced_at   TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_group_realm_name UNIQUE (realm_id, display_name)
);
CREATE INDEX IF NOT EXISTS ix_scim_group_realm  ON scim_group(realm_id);
CREATE INDEX IF NOT EXISTS ix_scim_group_ext_id ON scim_group(external_id);

CREATE TABLE IF NOT EXISTS scim_group_member (
  id        UUID PRIMARY KEY,
  group_id  UUID NOT NULL REFERENCES scim_group(id) ON DELETE CASCADE,
  user_id   UUID NOT NULL REFERENCES scim_user(id)  ON DELETE CASCADE,
  CONSTRAINT uq_scim_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_scim_group_member_group ON scim_group_member(group_id);
CREATE INDEX IF NOT EXISTS ix_scim_group_member_user  ON scim_group_member(user_id);
