CREATE TABLE scim_user (
  id               VARCHAR2(36)  NOT NULL PRIMARY KEY,
  realm_id         VARCHAR2(36)  NOT NULL,
  external_id      VARCHAR2(255),
  user_name        VARCHAR2(255) NOT NULL,
  display_name     VARCHAR2(255),
  given_name       VARCHAR2(255),
  family_name      VARCHAR2(255),
  email            VARCHAR2(255),
  active           NUMBER(1)     DEFAULT 1 NOT NULL,
  provisioned_at   TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_synced_at   TIMESTAMP WITH TIME ZONE,
  iam_user_id      VARCHAR2(36),
  CONSTRAINT uq_scim_user_realm_username UNIQUE (realm_id, user_name)
);
CREATE INDEX ix_scim_user_realm    ON scim_user(realm_id);
CREATE INDEX ix_scim_user_ext_id   ON scim_user(external_id);
CREATE INDEX ix_scim_user_iam_user ON scim_user(iam_user_id);

CREATE TABLE scim_group (
  id               VARCHAR2(36)  NOT NULL PRIMARY KEY,
  realm_id         VARCHAR2(36)  NOT NULL,
  external_id      VARCHAR2(255),
  display_name     VARCHAR2(255) NOT NULL,
  provisioned_at   TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_synced_at   TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_scim_group_realm_name UNIQUE (realm_id, display_name)
);
CREATE INDEX ix_scim_group_realm  ON scim_group(realm_id);
CREATE INDEX ix_scim_group_ext_id ON scim_group(external_id);

CREATE TABLE scim_group_member (
  id        VARCHAR2(36) NOT NULL PRIMARY KEY,
  group_id  VARCHAR2(36) NOT NULL,
  user_id   VARCHAR2(36) NOT NULL,
  CONSTRAINT uq_scim_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX ix_scim_group_member_group ON scim_group_member(group_id);
CREATE INDEX ix_scim_group_member_user  ON scim_group_member(user_id);
