CREATE TABLE IF NOT EXISTS password_reset_token (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  request_ip TEXT,
  user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_hash ON password_reset_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset_token(user_id);

CREATE TABLE IF NOT EXISTS email_verification_token (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  request_ip TEXT,
  user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_email_verification_token_hash ON email_verification_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_email_verification_user ON email_verification_token(user_id);

