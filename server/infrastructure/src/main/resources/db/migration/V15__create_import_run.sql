CREATE TABLE import_run (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    importer TEXT NOT NULL,
    -- Never holds an abandoned state: a run that stops advancing is read as abandoned and its row
    -- left exactly as the process that died left it.
    state TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    -- The only evidence that the process behind an in-progress row is still alive. Nothing sweeps
    -- these rows, so the guard derives liveness from this column and never writes the verdict.
    last_advanced_at TIMESTAMPTZ NOT NULL,
    added INT NOT NULL DEFAULT 0,
    refreshed INT NOT NULL DEFAULT 0
);

-- No partial unique index admitting a single in-progress row. Its predicate would have to
-- reference now() to step over a run past the liveness bound, which an index predicate cannot do,
-- and inserting past such a row would mean writing the abandoned state this table declines to
-- store. A transaction-scoped advisory lock taken inside the claim serialises it instead — which
-- is why a run row is inserted in exactly one place, and why a second insertion path would step
-- past the guard silently. Nothing beyond the keys is indexed: this is a handful of rows a day.

CREATE TABLE import_run_organo (
    run_id UUID NOT NULL
        CONSTRAINT import_run_organo_run_id_fkey REFERENCES import_run (id),
    organo_id UUID NOT NULL
        CONSTRAINT import_run_organo_organo_id_fkey REFERENCES organo_contratacion (id),
    state TEXT NOT NULL,
    added INT NOT NULL DEFAULT 0,
    refreshed INT NOT NULL DEFAULT 0,
    failure_reason TEXT,
    -- The covered Órganos are enumerated when the run is triggered, not discovered as the run
    -- reaches them, so a run that dies at the fortieth of four hundred can still name the list it
    -- was going to cover — and no run can cover an Órgano twice.
    CONSTRAINT import_run_organo_pkey PRIMARY KEY (run_id, organo_id)
);
