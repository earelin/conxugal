-- A second import state beside contrato_menor_import_state rather than one family-keyed table.
-- The two cursors are different kinds of thing: contratos menores resume from a date inside a
-- windowed walk, licitacións from an offset into an ordered listing. One shared table would carry
-- a nullable column for each plus a discriminator deciding which is meaningful -- three columns to
-- express what two tables express with none. Neither family's progress can then be read as the
-- other's, which is easiest to guarantee when there is no shared row to get wrong.
CREATE TABLE licitacion_import_state (
    -- The primary key is also the foreign key: this row's identity is the Órgano's, so there is
    -- one per Órgano or none, and an Órgano with no row has never been imported.
    organo_id UUID PRIMARY KEY
        CONSTRAINT licitacion_import_state_organo_id_fkey
        REFERENCES organo_contratacion (id),
    state TEXT NOT NULL,
    -- The offset already consumed. Null because the row is created before any page commits, and
    -- written after each page's procedures do -- so a crash between the two leaves it behind what
    -- is stored and the resumption re-reads the overlap.
    cursor_offset INT
);

-- No instant this family's history is covered through. Its sibling carries one because the
-- contratos menores incremental window is measured from it; this family's incremental mode is
-- driven by the listing's last-modified ordering instead, and a column nothing has asked for yet
-- would be a guess about how that feature works.

-- The records of one Órgano whose retrieval or parse failed: a set of identifiers the next run
-- tries once more, emptied as entries succeed. Deliberately not a retry queue -- no attempt count,
-- no backoff, no next-attempt time -- because that machinery answers a problem nobody has measured.
CREATE TABLE licitacion_outstanding_record (
    organo_id UUID NOT NULL
        CONSTRAINT licitacion_outstanding_record_organo_id_fkey
        REFERENCES organo_contratacion (id),
    -- TEXT, matching licitacion.publication_id: this holds identifiers to come back to, and one it
    -- could not represent would be one the walk could never retry.
    publication_id TEXT NOT NULL,
    -- The four values the record cannot answer for itself: the source publishes them on the listing
    -- entry, and a retry happens before the cursor resumes, with no listing entry in hand and no
    -- way to ask the source for one row.
    publication_date DATE,
    last_modified DATE,
    -- The published state, held as its two values rather than as a foreign key to licitacion_state.
    -- An entry records what the listing said; the row it will need may not exist yet, and pointing
    -- at one would make the ledger depend on a write it does not perform. The retry upserts the
    -- state from these two, exactly as the listing path does.
    state_code INT NOT NULL,
    state_label TEXT,
    CONSTRAINT licitacion_outstanding_record_pkey PRIMARY KEY (organo_id, publication_id)
);

-- No index beyond the key: organo_id is leftmost in it, which is what both reads narrow by.
