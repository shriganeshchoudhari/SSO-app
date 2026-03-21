CREATE TABLE signing_key (
  id              VARCHAR2(36)  NOT NULL PRIMARY KEY,
  realm_id        VARCHAR2(36),
  key_id          VARCHAR2(128) NOT NULL,
  algorithm       VARCHAR2(16)  DEFAULT 'RS256' NOT NULL,
  private_key_enc CLOB          NOT NULL,
  public_key_pem  CLOB          NOT NULL,
  created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  retired_at      TIMESTAMP WITH TIME ZONE,
  expires_at      TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_signing_key_kid     ON signing_key(key_id);
CREATE        INDEX ix_signing_key_realm   ON signing_key(realm_id);
CREATE        INDEX ix_signing_key_retired ON signing_key(retired_at);
