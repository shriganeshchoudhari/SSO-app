CREATE TABLE IF NOT EXISTS scim_user (
  id               CHAR(36)      NOT NULL PRIMARY KEY,
  realm_id         CHAR(36)      NOT NULL,
  external_id      VARCHAR(255),
  user_name        VARCHAR(255)  NOT NULL,
  display_name     VARCHAR(255),
  given_name       VARCHAR(255),
  family_name      VARCHAR(255),
  email            VARCHAR(255),
  active           TINYINT(1)    NOT NULL DEFAULT 1,
  provisioned_at   DATETIME(6)   NOT NULL DEFAULT NOW(6),
  last_synced_at   DATETIME(6),
  iam_user_id      CHAR(36),
  CONSTRAINT uq_scim_user_realm_username UNIQUE (realm_id, user_name)
);
CREATE INDEX ix_scim_user_realm    ON scim_user(realm_id);
CREATE INDEX ix_scim_user_ext_id   ON scim_user(external_id);
CREATE INDEX ix_scim_user_iam_user ON scim_user(iam_user_id);

CREATE TABLE IF NOT EXISTS scim_group (
  id               CHAR(36)      NOT NULL PRIMARY KEY,
  realm_id         CHAR(36)      NOT NULL,
  external_id      VARCHAR(255),
  display_name     VARCHAR(255)  NOT NULL,
  provisioned_at   DATETIME(6)   NOT NULL DEFAULT NOW(6),
  last_synced_at   DATETIME(6),
  CONSTRAINT uq_scim_group_realm_name UNIQUE (realm_id, display_name)
);
CREATE INDEX ix_scim_group_realm  ON scim_group(realm_id);
CREATE INDEX ix_scim_group_ext_id ON scim_group(external_id);

CREATE TABLE IF NOT EXISTS scim_group_member (
  id        CHAR(36) NOT NULL PRIMARY KEY,
  group_id  CHAR(36) NOT NULL,
  user_id   CHAR(36) NOT NULL,
  CONSTRAINT uq_scim_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX ix_scim_group_member_group ON scim_group_member(group_id);
CREATE INDEX ix_scim_group_member_user  ON scim_group_member(user_id);
