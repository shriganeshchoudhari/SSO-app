CREATE TABLE IF NOT EXISTS login_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  user_id CHAR(36) NULL,
  client_id CHAR(36) NULL,
  type VARCHAR(64) NOT NULL,
  time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  ip_address VARCHAR(128),
  details TEXT,
  CONSTRAINT fk_le_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_le_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE SET NULL,
  CONSTRAINT fk_le_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE SET NULL
);

CREATE INDEX idx_le_realm_time ON login_event(realm_id, time);

CREATE TABLE IF NOT EXISTS admin_audit_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  actor_user_id CHAR(36) NULL,
  action VARCHAR(128) NOT NULL,
  resource_type VARCHAR(128) NOT NULL,
  resource_id VARCHAR(255),
  time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  ip_address VARCHAR(128),
  details TEXT,
  CONSTRAINT fk_aae_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_aae_actor FOREIGN KEY (actor_user_id) REFERENCES iam_user(id) ON DELETE SET NULL
);

CREATE INDEX idx_aae_realm_time ON admin_audit_event(realm_id, time);

