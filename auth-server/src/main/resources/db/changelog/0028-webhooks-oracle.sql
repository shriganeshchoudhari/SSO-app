CREATE TABLE webhook_endpoint (
  id                    VARCHAR2(36) NOT NULL PRIMARY KEY,
  realm_id              VARCHAR2(36) NOT NULL,
  name                  VARCHAR2(255) NOT NULL,
  url                   VARCHAR2(2000) NOT NULL,
  signing_secret        CLOB,
  subscribed_events_raw VARCHAR2(2000),
  enabled               NUMBER(1) DEFAULT 1 NOT NULL,
  created_at            TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  last_delivery_at      TIMESTAMP WITH TIME ZONE,
  last_failure_at       TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uq_webhook_endpoint_realm_name UNIQUE (realm_id, name)
);

CREATE INDEX ix_webhook_endpoint_realm ON webhook_endpoint(realm_id);

CREATE TABLE webhook_delivery (
  id              VARCHAR2(36) NOT NULL PRIMARY KEY,
  realm_id        VARCHAR2(36) NOT NULL,
  endpoint_id     VARCHAR2(36) NOT NULL,
  event_category  VARCHAR2(64) NOT NULL,
  event_type      VARCHAR2(255) NOT NULL,
  event_id        VARCHAR2(255),
  request_body    CLOB NOT NULL,
  response_status NUMBER(10),
  response_body   CLOB,
  error_message   CLOB,
  success         NUMBER(1) DEFAULT 0 NOT NULL,
  attempted_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  completed_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_webhook_delivery_endpoint ON webhook_delivery(endpoint_id);
CREATE INDEX ix_webhook_delivery_realm ON webhook_delivery(realm_id);
CREATE INDEX ix_webhook_delivery_attempted_at ON webhook_delivery(attempted_at);
