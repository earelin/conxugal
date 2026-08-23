-- Who bid, on which lote, whether they won, and which firms made up a consortium.
--
-- A UTE is an operador económico, not a property of a bid, so the membership lives beside the
-- catalogue rather than beside the procedure and both of its ends are catalogue entries. That is
-- what lets it be read in both directions -- who was this consortium made of, and what has this
-- firm been part of -- for a consortium the source identified and one it did not alike.

-- The identifier stops being required, for exactly one party: a UTE the source names, structures
-- and lists the members of while publishing no identifier for it -- 33 of 35 measured. It is
-- catalogued under the bid that published it and holds no identifier at all.
--
-- The UNIQUE stays as it is, and must: PostgreSQL treats nulls as distinct by default, so every
-- real identifier is still unique while identifier-less UTEs coexist without colliding. Writing
-- NULLS NOT DISTINCT here would collapse every one of them onto a single row -- unrelated firms
-- pooled under one identity, which is the failure the placeholder rule exists to prevent.
--
-- A placeholder still never becomes an identity. This column holds null for such a UTE, never `-`
-- and never a TEMP- value, so two consortia the source stamped with the same placeholder remain
-- two rows and are never attached to each other.
ALTER TABLE operador_economico ALTER COLUMN fiscal_id DROP NOT NULL;

-- The one kind the catalogue records. It declines to store whether an operador is a natural person
-- or a legal entity, because that would have to be derived from the shape of an identifier; this is
-- the opposite case -- the source publishes it, structurally, by listing the member firms beneath
-- the party -- and it is a fact about a consortium of firms rather than about a person.
ALTER TABLE operador_economico ADD COLUMN ute BOOLEAN NOT NULL DEFAULT FALSE;

-- What bounds the nullable identifier to the party it was made nullable for. Without it the column
-- would read as optional for everybody, and a resolution defect would quietly catalogue an ordinary
-- firm the catalogue can never find again. The domain record refuses the same shape, so a defect
-- fails where the mistake is; this is the guarantee behind it.
ALTER TABLE operador_economico
    ADD CONSTRAINT operador_economico_fiscal_id_check CHECK (fiscal_id IS NOT NULL OR ute);

CREATE TABLE operador_ute_membership (
    ute_id UUID NOT NULL
        CONSTRAINT operador_ute_membership_ute_id_fkey REFERENCES operador_economico (id),
    operador_economico_id UUID NOT NULL
        CONSTRAINT operador_ute_membership_operador_economico_id_fkey
            REFERENCES operador_economico (id),
    -- Its own marker rather than a reading of either end's: one visible UTE membership is enough to
    -- keep an operador reachable, so a member firm whose only tie is a membership no visible bid
    -- still publishes would stay reachable through an invisible fact. What sets it is the
    -- reconciliation; this is somewhere for it to write.
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    -- The pair is the identity, so it is the primary key and there is no surrogate beside it: two
    -- non-null foreign keys leave nothing for one to add, and a member the source lists twice
    -- collapses here rather than in the caller.
    CONSTRAINT operador_ute_membership_pkey PRIMARY KEY (ute_id, operador_economico_id),
    -- A row saying nothing, and worse than nothing: it would let a consortium keep itself reachable
    -- under a predicate that counts one visible membership. The domain record refuses the same
    -- shape so a defect fails where the mistake is; this is the guarantee behind it.
    CONSTRAINT operador_ute_membership_distinct_ends_check CHECK (ute_id <> operador_economico_id)
);

-- One published bidder for one award point. It holds a reference to the party and no copy of its
-- name -- including for a consortium, which is now an operador like any other and carries its
-- published name there. The consortium marker and the published name an earlier draft put here,
-- and the CHECK that bound them together, are gone with it.
CREATE TABLE licitacion_participation (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    licitacion_id UUID NOT NULL
        CONSTRAINT licitacion_participation_licitacion_id_fkey REFERENCES licitacion (id),
    lote_id UUID
        CONSTRAINT licitacion_participation_lote_id_fkey REFERENCES licitacion_lote (id),
    -- Null where the published identifier was unusable: that bidder resolves to nobody and is
    -- recorded as neither participant nor awardee of any catalogue entry, rather than dropped.
    operador_economico_id UUID
        CONSTRAINT licitacion_participation_operador_economico_id_fkey
            REFERENCES operador_economico (id),
    -- The award carried back to the bid that won it, so an operador that won one lote of a
    -- procedure and lost another holds a row for each.
    won BOOLEAN NOT NULL DEFAULT FALSE,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULLS NOT DISTINCT on licitacion_cpv's reasoning: two of these three components are null on
    -- the ordinary lotless procedure whose bidder resolved to nobody, and PostgreSQL's default
    -- would insert a fresh row for that bidder on every re-import.
    CONSTRAINT licitacion_participation_key UNIQUE NULLS NOT DISTINCT
        (licitacion_id, lote_id, operador_economico_id)
);

-- The natural key, the primary key and the foreign keys, and nothing else -- V19's rule, including
-- its corollary: licitacion_id is already leftmost in the participation's unique constraint and
-- ute_id leftmost in the membership's primary key, so neither is indexed a second time.
--
-- In particular no (licitacion_id, lote_id). An earlier draft added it for the bidder-count
-- cross-check and for the procedure's page; the cross-check never reads the database at all, being
-- performed at parse time, and the page is the browsing feature's read to measure and index.
CREATE INDEX licitacion_participation_lote_id_idx ON licitacion_participation (lote_id);
CREATE INDEX licitacion_participation_operador_economico_id_idx
    ON licitacion_participation (operador_economico_id);

-- The membership read from the member's end, which its primary key cannot serve: that key leads
-- with the UTE, so finding the UTEs one firm belongs to would otherwise scan.
CREATE INDEX operador_ute_membership_operador_economico_id_idx
    ON operador_ute_membership (operador_economico_id);
