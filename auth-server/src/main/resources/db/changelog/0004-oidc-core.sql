ALTER TABLE client ADD COLUMN IF NOT EXISTS redirect_uris_raw TEXT;
ALTER TABLE client ADD COLUMN IF NOT EXISTS grant_types_raw TEXT;

CREATE TABLE IF NOT EXISTS authorization_code (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  user_session_id UUID NOT NULL REFERENCES user_session(id) ON DELETE CASCADE,
  code_hash VARCHAR(128) NOT NULL,
  redirect_uri TEXT NOT NULL,
  scope TEXT,
  code_challenge VARCHAR(255),
  code_challenge_method VARCHAR(32),
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_authorization_code_hash ON authorization_code(code_hash);

CREATE TABLE IF NOT EXISTS refresh_token (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  user_session_id UUID NOT NULL REFERENCES user_session(id) ON DELETE CASCADE,
  token_hash VARCHAR(128) NOT NULL,
  scope TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_refresh_token_hash ON refresh_token(token_hash);
