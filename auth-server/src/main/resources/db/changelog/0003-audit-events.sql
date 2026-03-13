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

CREATE INDEX IF NOT EXISTS idx_login_event_realm_time ON login_event(realm_id, time);

CREATE TABLE IF NOT EXISTS admin_audit_event (
  id BIGSERIAL PRIMARY KEY,
  realm_id UUID REFERENCES realm(id) ON DELETE CASCADE,
  actor_user_id UUID NULL REFERENCES iam_user(id) ON DELETE SET NULL,
  action TEXT NOT NULL,
  resource_type TEXT NOT NULL,
  resource_id TEXT,
  time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_address INET,
  details JSONB
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_realm_time ON admin_audit_event(realm_id, time);

