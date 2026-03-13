CREATE TABLE IF NOT EXISTS realm (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  enabled TINYINT(1) DEFAULT 1
);

CREATE TABLE IF NOT EXISTS iam_user (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  username VARCHAR(255) NOT NULL,
  email VARCHAR(320),
  enabled TINYINT(1) DEFAULT 1,
  email_verified TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_realm_username UNIQUE (realm_id, username)
);

CREATE TABLE IF NOT EXISTS client (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  client_id VARCHAR(255) NOT NULL,
  protocol VARCHAR(64) NOT NULL,
  secret VARCHAR(255),
  public_client TINYINT(1) DEFAULT 0,
  CONSTRAINT fk_client_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_realm_client UNIQUE (realm_id, client_id)
);

CREATE TABLE IF NOT EXISTS role (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  name VARCHAR(255) NOT NULL,
  client_id CHAR(36) NULL,
  CONSTRAINT fk_role_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_role_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT uq_role UNIQUE (realm_id, name, client_id)
);

CREATE TABLE IF NOT EXISTS user_role (
  user_id CHAR(36) NOT NULL,
  role_id CHAR(36) NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS credential (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  type VARCHAR(64) NOT NULL,
  value_hash VARCHAR(4000) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cred_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_session (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  started DATETIME NOT NULL,
  last_refresh DATETIME NOT NULL,
  CONSTRAINT fk_us_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS client_session (
  id CHAR(36) PRIMARY KEY,
  user_session_id CHAR(36) NOT NULL,
  client_id CHAR(36) NOT NULL,
  CONSTRAINT fk_cs_us FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT fk_cs_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
