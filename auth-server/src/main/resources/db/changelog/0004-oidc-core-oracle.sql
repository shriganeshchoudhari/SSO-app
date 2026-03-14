ALTER TABLE client ADD (redirect_uris_raw CLOB);
ALTER TABLE client ADD (grant_types_raw CLOB);

CREATE TABLE authorization_code (
  id RAW(16) PRIMARY KEY,
  realm_id RAW(16) NOT NULL,
  client_id RAW(16) NOT NULL,
  user_id RAW(16) NOT NULL,
  user_session_id RAW(16) NOT NULL,
  code_hash VARCHAR2(128 CHAR) NOT NULL,
  redirect_uri CLOB NOT NULL,
  scope CLOB NULL,
  code_challenge VARCHAR2(255 CHAR) NULL,
  code_challenge_method VARCHAR2(32 CHAR) NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT fk_ac_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_ac_session FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT uq_ac_hash UNIQUE (code_hash)
);

CREATE TABLE refresh_token (
  id RAW(16) PRIMARY KEY,
  realm_id RAW(16) NOT NULL,
  client_id RAW(16) NOT NULL,
  user_id RAW(16) NOT NULL,
  user_session_id RAW(16) NOT NULL,
  token_hash VARCHAR2(128 CHAR) NOT NULL,
  scope CLOB NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT fk_rt_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_rt_session FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT uq_rt_hash UNIQUE (token_hash)
);
