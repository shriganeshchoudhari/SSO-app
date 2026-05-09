CREATE TABLE IF NOT EXISTS webhook_endpoint (
  id                    CHAR(36) NOT NULL PRIMARY KEY,
  realm_id              CHAR(36) NOT NULL,
  name                  VARCHAR(255) NOT NULL,
  url                   VARCHAR(2000) NOT NULL,
  signing_secret        TEXT,
  subscribed_events_raw VARCHAR(2000),
  enabled               TINYINT(1) NOT NULL DEFAULT 1,
  created_at            DATETIME(6) NOT NULL DEFAULT NOW(6),
  last_delivery_at      DATETIME(6),
  last_failure_at       DATETIME(6),
  CONSTRAINT uq_webhook_endpoint_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX ix_webhook_endpoint_realm ON webhook_endpoint(realm_id);

CREATE TABLE IF NOT EXISTS webhook_delivery (
  id              CHAR(36) NOT NULL PRIMARY KEY,
  realm_id        CHAR(36) NOT NULL,
  endpoint_id     CHAR(36) NOT NULL,
  event_category  VARCHAR(64) NOT NULL,
  event_type      VARCHAR(255) NOT NULL,
  event_id        VARCHAR(255),
  request_body    TEXT NOT NULL,
  response_status INT,
  response_body   TEXT,
  error_message   TEXT,
  success         TINYINT(1) NOT NULL DEFAULT 0,
  attempted_at    DATETIME(6) NOT NULL DEFAULT NOW(6),
  completed_at    DATETIME(6)
);

CREATE INDEX ix_webhook_delivery_endpoint ON webhook_delivery(endpoint_id);
CREATE INDEX ix_webhook_delivery_realm ON webhook_delivery(realm_id);
CREATE INDEX ix_webhook_delivery_attempted_at ON webhook_delivery(attempted_at);
