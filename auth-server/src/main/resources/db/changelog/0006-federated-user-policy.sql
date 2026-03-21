ALTER TABLE iam_user
  ADD COLUMN federation_source TEXT;

ALTER TABLE iam_user
  ADD COLUMN federation_provider_id UUID;
