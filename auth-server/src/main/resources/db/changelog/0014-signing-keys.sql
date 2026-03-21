-- Persisted signing key pairs for JWT issuance.
-- The active key is the one where retired_at IS NULL.
-- On rotation the old key is retired (retired_at set) but kept during the
-- grace window so tokens signed with the old kid remain verifiable.

CREATE TABLE IF NOT EXISTS signing_key (
  id              UUID          PRIMARY KEY,
  realm_id        UUID          REFERENCES realm(id) ON DELETE CASCADE,
  key_id          VARCHAR(128)  NOT NULL,
  algorithm       VARCHAR(16)   NOT NULL DEFAULT 'RS256',
  private_key_enc TEXT          NOT NULL,
  public_key_pem  TEXT          NOT NULL,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  retired_at      TIMESTAMPTZ,
  expires_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_signing_key_kid     ON signing_key(key_id);
CREATE        INDEX IF NOT EXISTS ix_signing_key_realm   ON signing_key(realm_id);
CREATE        INDEX IF NOT EXISTS ix_signing_key_retired ON signing_key(retired_at);
