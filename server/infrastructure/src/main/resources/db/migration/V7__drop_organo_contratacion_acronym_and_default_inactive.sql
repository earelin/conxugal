ALTER TABLE organo_contratacion DROP COLUMN acronym;
ALTER TABLE organo_contratacion ALTER COLUMN active SET DEFAULT FALSE;
