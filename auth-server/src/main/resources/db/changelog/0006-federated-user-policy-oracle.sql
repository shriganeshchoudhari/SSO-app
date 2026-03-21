ALTER TABLE iam_user ADD (
  federation_source VARCHAR2(64),
  federation_provider_id CHAR(36)
);
