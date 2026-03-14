ALTER TABLE client ADD COLUMN redirect_uris_raw TEXT NULL;
ALTER TABLE client ADD COLUMN grant_types_raw TEXT NULL;

CREATE TABLE authorization_code (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  client_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  user_session_id CHAR(36) NOT NULL,
  code_hash VARCHAR(128) NOT NULL,
  redirect_uri TEXT NOT NULL,
  scope TEXT NULL,
  code_challenge VARCHAR(255) NULL,
  code_challenge_method VARCHAR(32) NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ac_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_session FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT uq_ac_hash UNIQUE (code_hash)
);

CREATE TABLE refresh_token (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  client_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  user_session_id CHAR(36) NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  scope TEXT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_rt_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_session FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT uq_rt_hash UNIQUE (token_hash)
);
