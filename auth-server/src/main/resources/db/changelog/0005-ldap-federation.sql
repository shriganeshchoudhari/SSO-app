CREATE TABLE IF NOT EXISTS ldap_provider (
  id UUID PRIMARY KEY,
  realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  url TEXT NOT NULL,
  bind_dn TEXT,
  bind_credential TEXT,
  user_search_base TEXT,
  user_search_filter TEXT,
  username_attribute TEXT,
  email_attribute TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(realm_id, name)
);
