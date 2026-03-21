CREATE TABLE IF NOT EXISTS signing_key (
  id              CHAR(36)      NOT NULL PRIMARY KEY,
  realm_id        CHAR(36),
  key_id          VARCHAR(128)  NOT NULL,
  algorithm       VARCHAR(16)   NOT NULL DEFAULT 'RS256',
  private_key_enc MEDIUMTEXT    NOT NULL,
  public_key_pem  MEDIUMTEXT    NOT NULL,
  created_at      DATETIME(6)   NOT NULL DEFAULT NOW(6),
  retired_at      DATETIME(6),
  expires_at      DATETIME(6)
);

CREATE UNIQUE INDEX ux_signing_key_kid     ON signing_key(key_id);
CREATE        INDEX ix_signing_key_realm   ON signing_key(realm_id);
CREATE        INDEX ix_signing_key_retired ON signing_key(retired_at);
