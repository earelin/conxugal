CREATE TABLE contrato_menor_import_state (
    -- The primary key is also the foreign key: this row's identity is the Órgano's, so there is
    -- one per Órgano or none, and an Órgano with no row has never been imported.
    organo_id UUID PRIMARY KEY
        CONSTRAINT contrato_menor_import_state_organo_id_fkey
        REFERENCES organo_contratacion (id),
    state TEXT NOT NULL,
    cursor_date DATE,
    -- T₀: when the initial import's *first* window was taken. Stamped once, at creation, and
    -- carried unchanged across every resumption — an incremental window measured from a later
    -- resumption's start would skip everything published in between.
    covered_through TIMESTAMPTZ NOT NULL
);

-- Its own table rather than four more columns on organo_contratacion: that row is
-- update-in-place territory for catalogue reconciliation and is read by every catalogue read,
-- while this one is rewritten after every batch for days. Deliberately carrying no run
-- identifier and no foreign key to one, so pruning run history cannot strand a half-loaded
-- Órgano with nowhere to resume from.
