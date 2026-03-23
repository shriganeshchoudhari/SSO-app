CREATE TABLE organization (
  id           VARCHAR2(36)  NOT NULL PRIMARY KEY,
  realm_id     VARCHAR2(36)  NOT NULL,
  name         VARCHAR2(255) NOT NULL,
  display_name VARCHAR2(255),
  enabled      NUMBER(1)     DEFAULT 1 NOT NULL,
  created_at   TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_org_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX ix_organization_realm ON organization(realm_id);

CREATE TABLE organization_member (
  id              VARCHAR2(36) NOT NULL PRIMARY KEY,
  organization_id VARCHAR2(36) NOT NULL,
  user_id         VARCHAR2(36) NOT NULL,
  org_role        VARCHAR2(64) DEFAULT 'member' NOT NULL,
  joined_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);

CREATE INDEX ix_org_member_org  ON organization_member(organization_id);
CREATE INDEX ix_org_member_user ON organization_member(user_id);
