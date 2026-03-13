CREATE TABLE IF NOT EXISTS realm (
  id UUID PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  display_name TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS client (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  client_id TEXT NOT NULL,
  protocol TEXT NOT NULL,
  secret TEXT,
  redirect_uris TEXT[],
  public_client BOOLEAN DEFAULT FALSE,
  UNIQUE(realm_id, client_id)
);

CREATE TABLE IF NOT EXISTS iam_user (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  username TEXT NOT NULL,
  email TEXT,
  enabled BOOLEAN DEFAULT TRUE,
  email_verified BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(realm_id, username)
);

CREATE TABLE IF NOT EXISTS credential (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  value_hash TEXT NOT NULL,
  salt BYTEA,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS role (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  client_id UUID NULL REFERENCES client(id) ON DELETE CASCADE,
  UNIQUE(realm_id, name, client_id)
);

CREATE TABLE IF NOT EXISTS user_role (
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  role_id UUID REFERENCES role(id) ON DELETE CASCADE,
  PRIMARY KEY(user_id, role_id)
);

CREATE TABLE IF NOT EXISTS "group" (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  parent_id UUID NULL REFERENCES "group"(id) ON DELETE SET NULL,
  UNIQUE(realm_id, name, parent_id)
);

CREATE TABLE IF NOT EXISTS user_group (
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  group_id UUID REFERENCES "group"(id) ON DELETE CASCADE,
  PRIMARY KEY(user_id, group_id)
);

CREATE TABLE IF NOT EXISTS group_role (
  group_id UUID REFERENCES "group"(id) ON DELETE CASCADE,
  role_id UUID REFERENCES role(id) ON DELETE CASCADE,
  PRIMARY KEY(group_id, role_id)
);

CREATE TABLE IF NOT EXISTS user_session (
  id UUID PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  user_id UUID REFERENCES iam_user(id) ON DELETE CASCADE,
  started TIMESTAMPTZ NOT NULL,
  last_refresh TIMESTAMPTZ NOT NULL,
  ip_address INET
);

CREATE TABLE IF NOT EXISTS client_session (
  id UUID PRIMARY KEY,
  user_session_id UUID REFERENCES user_session(id) ON DELETE CASCADE,
  client_id UUID REFERENCES client(id) ON DELETE CASCADE,
  scope TEXT[]
);

CREATE TABLE IF NOT EXISTS login_event (
  id BIGSERIAL PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  user_id UUID NULL REFERENCES iam_user(id) ON DELETE SET NULL,
  client_id UUID NULL REFERENCES client(id) ON DELETE SET NULL,
  type TEXT NOT NULL,
  time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_address INET,
  details JSONB
);
