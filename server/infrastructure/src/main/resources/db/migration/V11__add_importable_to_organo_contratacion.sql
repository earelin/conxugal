ALTER TABLE organo_contratacion
    -- Not a convenience: a newly discovered Órgano is inserted without naming this column,
    -- so the default is what keeps it out of the import until an administrator marks it.
    ADD COLUMN importable BOOLEAN NOT NULL DEFAULT FALSE;
