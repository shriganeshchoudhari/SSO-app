CREATE TABLE IF NOT EXISTS signing_key (
  id              UUID          PRIMARY KEY,
  realm_id        UUID          REFERENCES realm(id) ON DELETE CASCADE,
  key_id          VARCHAR(128)  NOT NULL,
  algorithm       VARCHAR(16)   NOT NULL DEFAULT 'RS256',
  private_key_enc CLOB          NOT NULL,
  public_key_pem  CLOB          NOT NULL,
  created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  retired_at      TIMESTAMP WITH TIME ZONE,
  expires_at      TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_signing_key_kid     ON signing_key(key_id);
CREATE        INDEX IF NOT EXISTS ix_signing_key_realm   ON signing_key(realm_id);
CREATE        INDEX IF NOT EXISTS ix_signing_key_retired ON signing_key(retired_at);
