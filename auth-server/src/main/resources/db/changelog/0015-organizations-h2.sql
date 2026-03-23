CREATE TABLE IF NOT EXISTS organization (
  id           UUID          PRIMARY KEY,
  realm_id     UUID          NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  name         VARCHAR(255)  NOT NULL,
  display_name VARCHAR(255),
  enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_org_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX IF NOT EXISTS ix_organization_realm ON organization(realm_id);

CREATE TABLE IF NOT EXISTS organization_member (
  id              UUID        PRIMARY KEY,
  organization_id UUID        NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
  user_id         UUID        NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  org_role        VARCHAR(64) NOT NULL DEFAULT 'member',
  joined_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);

CREATE INDEX IF NOT EXISTS ix_org_member_org  ON organization_member(organization_id);
CREATE INDEX IF NOT EXISTS ix_org_member_user ON organization_member(user_id);
