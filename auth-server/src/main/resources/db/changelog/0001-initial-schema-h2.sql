CREATE TABLE IF NOT EXISTS realm (
  id UUID PRIMARY KEY,
  name VARCHAR(255) UNIQUE NOT NULL,
  display_name VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS iam_user (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  username VARCHAR(255) NOT NULL,
  email VARCHAR(320),
  enabled BOOLEAN DEFAULT TRUE,
  email_verified BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_realm_username UNIQUE (realm_id, username)
);

CREATE TABLE IF NOT EXISTS client (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  client_id VARCHAR(255) NOT NULL,
  protocol VARCHAR(64) NOT NULL,
  secret VARCHAR(255),
  public_client BOOLEAN DEFAULT FALSE,
  CONSTRAINT fk_client_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_realm_client UNIQUE (realm_id, client_id)
);

CREATE TABLE IF NOT EXISTS role (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  name VARCHAR(255) NOT NULL,
  client_id UUID NULL,
  CONSTRAINT fk_role_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_role_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT uq_role UNIQUE (realm_id, name, client_id)
);

CREATE TABLE IF NOT EXISTS user_role (
  user_id UUID NOT NULL,
  role_id UUID NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS credential (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  type VARCHAR(64) NOT NULL,
  value_hash VARCHAR(4000) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cred_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_session (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL,
  user_id UUID NOT NULL,
  started TIMESTAMP NOT NULL,
  last_refresh TIMESTAMP NOT NULL,
  CONSTRAINT fk_us_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS client_session (
  id UUID PRIMARY KEY,
  user_session_id UUID NOT NULL,
  client_id UUID NOT NULL,
  CONSTRAINT fk_cs_us FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT fk_cs_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
