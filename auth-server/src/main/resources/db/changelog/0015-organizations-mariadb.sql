CREATE TABLE IF NOT EXISTS organization (
  id           CHAR(36)      NOT NULL PRIMARY KEY,
  realm_id     CHAR(36)      NOT NULL,
  name         VARCHAR(255)  NOT NULL,
  display_name VARCHAR(255),
  enabled      TINYINT(1)    NOT NULL DEFAULT 1,
  created_at   DATETIME(6)   NOT NULL DEFAULT NOW(6),
  CONSTRAINT uq_org_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX ix_organization_realm ON organization(realm_id);

CREATE TABLE IF NOT EXISTS organization_member (
  id              CHAR(36)    NOT NULL PRIMARY KEY,
  organization_id CHAR(36)    NOT NULL,
  user_id         CHAR(36)    NOT NULL,
  org_role        VARCHAR(64) NOT NULL DEFAULT 'member',
  joined_at       DATETIME(6) NOT NULL DEFAULT NOW(6),
  CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);

CREATE INDEX ix_org_member_org  ON organization_member(organization_id);
CREATE INDEX ix_org_member_user ON organization_member(user_id);
