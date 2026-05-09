CREATE TABLE IF NOT EXISTS webhook_endpoint (
  id                    UUID PRIMARY KEY,
  realm_id              UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  name                  VARCHAR(255) NOT NULL,
  url                   VARCHAR(2000) NOT NULL,
  signing_secret        TEXT,
  subscribed_events_raw VARCHAR(2000),
  enabled               BOOLEAN NOT NULL DEFAULT TRUE,
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  last_delivery_at      TIMESTAMP WITH TIME ZONE,
  last_failure_at       TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_webhook_endpoint_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX IF NOT EXISTS ix_webhook_endpoint_realm ON webhook_endpoint(realm_id);

CREATE TABLE IF NOT EXISTS webhook_delivery (
  id             UUID PRIMARY KEY,
  realm_id       UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
  endpoint_id    UUID NOT NULL REFERENCES webhook_endpoint(id) ON DELETE CASCADE,
  event_category VARCHAR(64) NOT NULL,
  event_type     VARCHAR(255) NOT NULL,
  event_id       VARCHAR(255),
  request_body   TEXT NOT NULL,
  response_status INTEGER,
  response_body  TEXT,
  error_message  TEXT,
  success        BOOLEAN NOT NULL DEFAULT FALSE,
  attempted_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  completed_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS ix_webhook_delivery_endpoint ON webhook_delivery(endpoint_id);
CREATE INDEX IF NOT EXISTS ix_webhook_delivery_realm ON webhook_delivery(realm_id);
CREATE INDEX IF NOT EXISTS ix_webhook_delivery_attempted_at ON webhook_delivery(attempted_at);
