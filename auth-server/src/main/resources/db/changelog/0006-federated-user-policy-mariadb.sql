ALTER TABLE iam_user ADD COLUMN federation_source VARCHAR(64);
ALTER TABLE iam_user ADD COLUMN federation_provider_id CHAR(36);
