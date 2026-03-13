CREATE TABLE realm (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR2(255) UNIQUE NOT NULL,
  display_name VARCHAR2(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  enabled NUMBER(1) DEFAULT 1
);

CREATE TABLE iam_user (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  username VARCHAR2(255) NOT NULL,
  email VARCHAR2(320),
  enabled NUMBER(1) DEFAULT 1,
  email_verified NUMBER(1) DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_realm_username UNIQUE (realm_id, username)
);

CREATE TABLE client (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  client_id VARCHAR2(255) NOT NULL,
  protocol VARCHAR2(64) NOT NULL,
  secret VARCHAR2(255),
  public_client NUMBER(1) DEFAULT 0,
  CONSTRAINT fk_client_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT uq_realm_client UNIQUE (realm_id, client_id)
);

CREATE TABLE role (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  name VARCHAR2(255) NOT NULL,
  client_id CHAR(36),
  CONSTRAINT fk_role_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_role_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
  CONSTRAINT uq_role UNIQUE (realm_id, name, client_id)
);

CREATE TABLE user_role (
  user_id CHAR(36) NOT NULL,
  role_id CHAR(36) NOT NULL,
  CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

CREATE TABLE credential (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  type VARCHAR2(64) NOT NULL,
  value_hash VARCHAR2(4000) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cred_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE TABLE user_session (
  id CHAR(36) PRIMARY KEY,
  realm_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  started TIMESTAMP NOT NULL,
  last_refresh TIMESTAMP NOT NULL,
  CONSTRAINT fk_us_realm FOREIGN KEY (realm_id) REFERENCES realm(id) ON DELETE CASCADE,
  CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE
);

CREATE TABLE client_session (
  id CHAR(36) PRIMARY KEY,
  user_session_id CHAR(36) NOT NULL,
  client_id CHAR(36) NOT NULL,
  CONSTRAINT fk_cs_us FOREIGN KEY (user_session_id) REFERENCES user_session(id) ON DELETE CASCADE,
  CONSTRAINT fk_cs_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
