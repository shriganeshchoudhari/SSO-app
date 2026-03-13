# Database Schema (PostgreSQL) — Initial

## Entities & Relations (high level)
- Realm 1—* Client, User, Role, Group
- User *—* Role (user_role), Group *—* Role (group_role), User *—* Group (user_group)
- Client may define client-specific roles
- Sessions and Events link to Realm/User/Client

## Compatibility Notes (Multi-DB)
- Primary target: PostgreSQL (full schema).
- Additional targets: H2/MySQL/MariaDB/Oracle with core tables (realm, iam_user) for now.
- Postgres-specific types (e.g., INET, JSONB, TEXT[]) appear in sessions/events/clients. These will be normalized or adapted per-DB in later migrations.
- Migrations are selected per database via Liquibase dbms-specific changeSets.
  - H2: UUID native; timestamps use CURRENT_TIMESTAMP; booleans supported.
  - MySQL/MariaDB: use CHAR(36) for UUIDs, DATETIME for timestamps, TINYINT(1) for booleans.
  - Oracle: use CHAR(36) for UUIDs, TIMESTAMP for timestamps, NUMBER(1) for booleans.

## Tables (DDL v0)
-- realm
CREATE TABLE realm (
  id UUID PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  display_name TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  enabled BOOLEAN DEFAULT TRUE
);

-- client
CREATE TABLE client (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  client_id TEXT NOT NULL,
  protocol TEXT NOT NULL,          -- 'openid-connect' or 'saml'
  secret TEXT,                     -- store hashed/enc
  redirect_uris TEXT[],            -- normalized
  public_client BOOLEAN DEFAULT FALSE,
  UNIQUE(realm_id, client_id)
);

-- "user"
CREATE TABLE iam_user (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  username TEXT NOT NULL,
  email TEXT,
  enabled BOOLEAN DEFAULT TRUE,
  email_verified BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(realm_id, username)
);

-- credential
CREATE TABLE credential (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  type TEXT NOT NULL,              -- password, totp, webauthn
  value_hash TEXT NOT NULL,
  salt BYTEA,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- role
CREATE TABLE role (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  client_id UUID NULL REFERENCES client(id) ON DELETE CASCADE,
  UNIQUE(realm_id, name, client_id)
);

-- mappings
CREATE TABLE user_role (
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  role_id UUID REFERENCES role(id) ON DELETE CASCADE,
  PRIMARY KEY(user_id, role_id)
);

CREATE TABLE "group" (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  parent_id UUID NULL REFERENCES "group"(id) ON DELETE SET NULL,
  UNIQUE(realm_id, name, parent_id)
);

CREATE TABLE user_group (
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  group_id UUID REFERENCES "group"(id) ON DELETE CASCADE,
  PRIMARY KEY(user_id, group_id)
);

CREATE TABLE group_role (
  group_id UUID REFERENCES "group"(id) ON DELETE CASCADE,
  role_id UUID REFERENCES role(id) ON DELETE CASCADE,
  PRIMARY KEY(group_id, role_id)
);

-- sessions
CREATE TABLE user_session (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  started TIMESTAMPTZ NOT NULL,
  last_refresh TIMESTAMPTZ NOT NULL,
  ip_address INET
);

CREATE TABLE client_session (
  id UUID PRIMARY KEY,
  user_session_id UUID REFERENCES user_session(id) ON DELETE CASCADE,
  client_id UUID REFERENCES client(id) ON DELETE CASCADE,
  scope TEXT[]
);

-- events
CREATE TABLE login_event (
  id BIGSERIAL PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  user_id UUID NULL REFERENCES iam_user(id) ON DELETE SET NULL,
  client_id UUID NULL REFERENCES client(id) ON DELETE SET NULL,
  type TEXT NOT NULL,       -- LOGIN, LOGOUT, ERROR
  time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_address INET,
  details JSONB
);
