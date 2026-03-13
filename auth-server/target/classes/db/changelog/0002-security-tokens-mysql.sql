CREATE TABLE IF NOT EXISTS password_reset_token (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  request_ip VARCHAR(128),
  user_agent VARCHAR(512),
  CONSTRAINT fk_prt_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_prt_token_hash ON password_reset_token(token_hash);
CREATE INDEX idx_prt_user ON password_reset_token(user_id);

CREATE TABLE IF NOT EXISTS email_verification_token (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  request_ip VARCHAR(128),
  user_agent VARCHAR(512),
  CONSTRAINT fk_evt_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_evt_token_hash ON email_verification_token(token_hash);
CREATE INDEX idx_evt_user ON email_verification_token(user_id);

