-- MFA policy columns on the realm table.
-- mfa_required: when true every login in this realm requires a TOTP credential.
-- mfa_policy:   future extensibility (e.g. 'optional', 'required', 'adaptive').

ALTER TABLE realm ADD COLUMN IF NOT EXISTS mfa_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE realm ADD COLUMN IF NOT EXISTS mfa_policy   VARCHAR(32) NOT NULL DEFAULT 'optional';
