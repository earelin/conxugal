-- Nullable, then backfilled, then constrained — rather than one ADD COLUMN ... NOT NULL DEFAULT
-- 'CONTRATOS_MENORES', which would be a shorter metadata-only step and is deliberately not taken:
-- a default would silently accept an insert that named no family, which is exactly the second
-- insertion path the claim is the only place allowed to take. The three-step form leaves an
-- instance running pre-V18 code unable to enumerate coverage against the migrated schema, which
-- costs nothing because the application is stopped and started rather than rolled.
ALTER TABLE import_run_organo ADD COLUMN family TEXT;

-- Total and unambiguous: every run recorded before this migration is a contratos menores run or a
-- catalogue run, and a catalogue run covers no Órganos at all.
UPDATE import_run_organo SET family = 'CONTRATOS_MENORES';

ALTER TABLE import_run_organo ALTER COLUMN family SET NOT NULL;

-- Supersedes V15's "no run can cover an Órgano twice": one run covers an Órgano once per contract
-- family, which is what lets a single trigger import both and report each separately.
ALTER TABLE import_run_organo DROP CONSTRAINT import_run_organo_pkey;
ALTER TABLE import_run_organo
    ADD CONSTRAINT import_run_organo_pkey PRIMARY KEY (run_id, organo_id, family);
