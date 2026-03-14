ALTER TABLE client ADD COLUMN IF NOT EXISTS redirect_uris_raw VARCHAR(4000);
ALTER TABLE client ADD COLUMN IF NOT EXISTS grant_types_raw VARCHAR(1000);

CREATE TABLE IF NOT EXISTS authorization_code (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  client_id UUID NOT NULL,
  user_id UUID NOT NULL,
  user_session_id UUID NOT NULL,
  code_hash VARCHAR(128) NOT NULL,
  redirect_uri VARCHAR(4000) NOT NULL,
  scope VARCHAR(1000),
  code_challenge VARCHAR(255),
  code_challenge_method VARCHAR(32),
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT fk_ac_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_session FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT uq_ac_hash UNIQUE (code_hash)
);

CREATE TABLE IF NOT EXISTS refresh_token (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  client_id UUID NOT NULL,
  user_id UUID NOT NULL,
  user_session_id UUID NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  scope VARCHAR(1000),
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT fk_rt_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_session FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT uq_rt_hash UNIQUE (token_hash)
);
