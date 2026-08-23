-- The published vocabularies come first: the procedure refers to all four, so they have to exist
-- before it does. None of them is seeded and nothing validates against them -- the state set is not
-- closed (code 7 was never observed and higher ones may exist), so an unseen value creates its row
-- inside the transaction that stores the procedure naming it. A seeded catalogue would turn an
-- unseen code into a foreign-key violation and a rejected procedure instead.
--
-- None carries a withdrawal marker either. These are not parts of a procedure the source restates;
-- they are the values it publishes, and a state no procedure references any more is still a state
-- the source published.
--
-- No column here is collated. Nothing orders a vocabulary yet: the reference lists a reader
-- narrows by are the browsing feature's, and it adds the collation with the read that needs it.
CREATE TABLE licitacion_state (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- The source's own identifier for a state, held under the name its JSON gives it.
    code INT NOT NULL,
    -- Deliberately unconstrained. Codes 101 and 102 are both published as "Histórico", so a UNIQUE
    -- here would reject the second real state the source publishes, and a filter keyed on the label
    -- would merge two states the source distinguishes.
    label TEXT,
    CONSTRAINT licitacion_state_code_key UNIQUE (code)
);

-- The three type vocabularies are structurally identical, and their published name is what an
-- import matches on: the record's <dd> carries no id for them and the listing endpoint has no type
-- field at all, so there is no source identifier to hold beside it.
CREATE TABLE licitacion_contract_type (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    CONSTRAINT licitacion_contract_type_name_key UNIQUE (name)
);

CREATE TABLE licitacion_procedure_type (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    CONSTRAINT licitacion_procedure_type_name_key UNIQUE (name)
);

CREATE TABLE licitacion_tramitacion_type (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    CONSTRAINT licitacion_tramitacion_type_name_key UNIQUE (name)
);

-- The two regulated European lists. Unprefixed, unlike the four licitacion_* vocabularies above,
-- because these are external standards rather than this source's own vocabulary -- a CPV code is
-- not this source's coinage and thousands of procedures across the system cite one.
--
-- Neither is seeded, and regulated is not the same as closed: CPV is versioned, the 2008 revision
-- retired codes the 2003 one issued, and this system imports procedures published across both. A
-- seeded copy would turn a code retired before it was taken into a rejected procedure.
--
-- The description is deliberately not unique, on licitacion_state.label's reasoning: an import
-- matches an entry on the code the list assigns, never on wording that is translated, revised and
-- shared across sibling entries. It is nullable and nothing populates it yet -- the record's CPV
-- and NUT tables publish the code alone -- so null means nobody has supplied one, never that the
-- entry has none.
CREATE TABLE cpv (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code TEXT NOT NULL,
    description TEXT,
    CONSTRAINT cpv_code_key UNIQUE (code)
);

CREATE TABLE nut (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code TEXT NOT NULL,
    description TEXT,
    CONSTRAINT nut_code_key UNIQUE (code)
);

CREATE TABLE licitacion (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- The source's own identifier, unique here so "no duplicates" holds at the store even when
    -- use-case logic slips, exactly as contrato_menor.source_id does for the other family. It names
    -- a publication rather than a licitación: measured, the source serves both families from one
    -- identifier space.
    --
    -- TEXT, not BIGINT, though every identifier measured is an integer. How the source mints them
    -- is the source's business, and one that changed shape would otherwise cost a column type, a
    -- migration and a re-import of every procedure rather than a parse at the adapter. The column
    -- is matched on and never ordered, summed or incremented -- the walk resumes by the order the
    -- listing endpoint applies at the source -- so text gives up nothing here. contrato_menor's
    -- stays BIGINT: widening a shipped column of millions is a different change.
    publication_id TEXT NOT NULL,
    organo_id UUID NOT NULL
        CONSTRAINT licitacion_organo_id_fkey REFERENCES organo_contratacion (id),
    -- Both dates come from the listing entry rather than from the record, which publishes neither.
    -- Nullable because a date that could not be interpreted stores as absent rather than rejecting
    -- the procedure; last_modified is what the incremental refresh will order on.
    publication_date DATE,
    last_modified DATE,
    -- Required, because the listing always publishes a state. The three types below are not: a
    -- record need not publish them, and an inner join over one of them would drop a real procedure.
    state_id UUID NOT NULL
        CONSTRAINT licitacion_state_id_fkey REFERENCES licitacion_state (id),
    expediente TEXT,
    -- Uncapped, like contrato_menor.obxecto: a bound here could only turn a long published object
    -- into a failed write that rejects a real procedure.
    obxecto TEXT,
    contract_type_id UUID
        CONSTRAINT licitacion_contract_type_id_fkey REFERENCES licitacion_contract_type (id),
    procedure_type_id UUID
        CONSTRAINT licitacion_procedure_type_id_fkey REFERENCES licitacion_procedure_type (id),
    tramitacion_type_id UUID
        CONSTRAINT licitacion_tramitacion_type_id_fkey REFERENCES licitacion_tramitacion_type (id),
    -- The source's own count of lotes, which can disagree with the lotes actually named: on
    -- procedure 822054 it said 2 while the lotes table was empty and the award table named both.
    lote_count INTEGER,
    -- Two different figures, and neither is an award: the budget includes VAT and the estimated
    -- value excludes it. The listing's "importe" is the budget, and taking it for an award is the
    -- mistake that would fill every total with budgets, silently.
    base_budget NUMERIC,
    estimated_value NUMERIC,
    -- Created here, empty, because adding it later to a table of millions is a different operation
    -- from creating it now. Nothing this feature does writes it: an absent procedure is retained
    -- unchanged, since absence at the source is not evidence of withdrawal, and the explicit
    -- removal that is meaningful belongs to a later feature.
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT licitacion_publication_id_key UNIQUE (publication_id)
);

CREATE TABLE licitacion_lote (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    licitacion_id UUID NOT NULL
        CONSTRAINT licitacion_lote_licitacion_id_fkey REFERENCES licitacion (id),
    -- What the source printed, held as published: a lote published as "05" is stored as "05".
    -- TEXT rather than an integer because it is measured not to be one -- OU0028, LU4001, LU4031
    -- and CO0642 were all observed in award-table lote cells over 240 procedures.
    lote_identifier TEXT NOT NULL,
    -- The same lote reduced to the one form everything compares on, written by the adapter from the
    -- shared normaliser rather than derived here, so the rule exists once and in one language.
    --
    -- It is the key, and lote_identifier is not, because the two disagree and the disagreement is
    -- reachable: the award table produced both "1" and "05" in one measured sample, so a procedure
    -- spelling one lote both ways -- or a source that respells the padding between imports -- would
    -- otherwise insert a second lote row where the normaliser says there is one. Keying on the
    -- published spelling would pass a no-duplicate test while the source could still break it.
    lote_key TEXT NOT NULL,
    -- Both extras are optional because the source frequently publishes neither: a lote's existence
    -- comes from the award table and the lotes table supplies decoration.
    description TEXT,
    estimated_value NUMERIC,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT licitacion_lote_key UNIQUE (licitacion_id, lote_key)
);

-- A classification row records that this procedure cites that entry, on that date, for that award
-- point. The code itself is not a column here: the entry is the list's, held once and referred to,
-- which is what lets a year's selection offer only the codes it actually contains rather than a
-- DISTINCT over a column repeated once per procedure per code.
--
-- lote_id is nullable, and a null means the procedure as a whole rather than unattached. That is
-- not a concession: on procedure 822054 -- two lotes, two separate awards -- every CPV and NUT row
-- was procedure-wide, so a model requiring a lote on a procedure that has them could not store what
-- the source publishes.
CREATE TABLE licitacion_cpv (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    licitacion_id UUID NOT NULL
        CONSTRAINT licitacion_cpv_licitacion_id_fkey REFERENCES licitacion (id),
    lote_id UUID CONSTRAINT licitacion_cpv_lote_id_fkey REFERENCES licitacion_lote (id),
    cpv_id UUID NOT NULL CONSTRAINT licitacion_cpv_cpv_id_fkey REFERENCES cpv (id),
    diffusion_date DATE,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULLS NOT DISTINCT is load-bearing, and it is why the primary key is the surrogate rather
    -- than this: PostgreSQL treats NULLs as distinct by default, so without it the procedure-wide
    -- row of a lotless procedure would insert afresh on every re-import -- which is every
    -- procedure, on every run -- and a null is not allowed in a primary key at all.
    CONSTRAINT licitacion_cpv_key UNIQUE NULLS NOT DISTINCT (licitacion_id, lote_id, cpv_id)
);

CREATE TABLE licitacion_nut (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    licitacion_id UUID NOT NULL
        CONSTRAINT licitacion_nut_licitacion_id_fkey REFERENCES licitacion (id),
    lote_id UUID CONSTRAINT licitacion_nut_lote_id_fkey REFERENCES licitacion_lote (id),
    nut_id UUID NOT NULL CONSTRAINT licitacion_nut_nut_id_fkey REFERENCES nut (id),
    diffusion_date DATE,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT licitacion_nut_key UNIQUE NULLS NOT DISTINCT (licitacion_id, lote_id, nut_id)
);

-- The award and the formalisation below are two tables on purpose, not one denormalised into two.
-- A procedure can be awarded without being formalised -- of 284 measured award rows, 112 sat on
-- procedures in a state that has no formalisation -- and the two can name different parties.
CREATE TABLE licitacion_award (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    licitacion_id UUID NOT NULL
        CONSTRAINT licitacion_award_licitacion_id_fkey REFERENCES licitacion (id),
    lote_id UUID CONSTRAINT licitacion_award_lote_id_fkey REFERENCES licitacion_lote (id),
    resolution TEXT,
    resolution_date DATE,
    -- The resolution table's "Importe" and nothing else. The listing's number is the base budget.
    amount NUMERIC,
    -- The stated period, as published prose ("12 meses", "2 meses 7 días"), not an interval: the
    -- source's spelling is what R33 stores, and interpreting it would be a reading rather than it.
    execution_period TEXT,
    -- The name the resolution published, kept beside the link rather than instead of it: an award
    -- no route resolves still names somebody, and that is what a reader is shown.
    awardee_name TEXT,
    operador_economico_id UUID
        CONSTRAINT licitacion_award_operador_economico_id_fkey REFERENCES operador_economico (id),
    -- How that operador was reached, never null: UNRESOLVED is the value an award nothing resolved
    -- carries, so the absence of a link is stated rather than inferred from a null beside a null.
    -- A later re-resolution is gated on it, which is why it is stored rather than derived.
    awardee_resolution_path TEXT NOT NULL,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT licitacion_award_key UNIQUE NULLS NOT DISTINCT (licitacion_id, lote_id)
);

CREATE TABLE licitacion_formalisation (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    licitacion_id UUID NOT NULL
        CONSTRAINT licitacion_formalisation_licitacion_id_fkey REFERENCES licitacion (id),
    lote_id UUID CONSTRAINT licitacion_formalisation_lote_id_fkey REFERENCES licitacion_lote (id),
    formalisation_date DATE,
    contratista_name TEXT,
    -- fiscal_identifier, not fiscal_id as operador_economico spells the same concept. The
    -- divergence is deliberate: that column is the catalogue's key, this one is what a single
    -- formalisation cell carried, which may identify nobody the catalogue holds. Naming them alike
    -- would suggest they are the same column.
    fiscal_identifier TEXT,
    nationality TEXT,
    amount NUMERIC,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT licitacion_formalisation_key UNIQUE NULLS NOT DISTINCT (licitacion_id, lote_id)
);

-- The unique natural keys above and the foreign keys, and nothing else. In particular no
-- (organo_id, publication_date): that is exactly what V13 created for contrato_menor and exactly
-- what V16 dropped again, its publication_date column being what neither browse read needed from
-- it. The browsing feature measures its own queries and adds what they ask for.
--
-- A foreign key already leftmost in a unique constraint is not indexed a second time --
-- licitacion_lote, licitacion_cpv, licitacion_nut, licitacion_award and licitacion_formalisation
-- all key on licitacion_id first, so their constraint's index is the one a cascade check or a join
-- from the procedure would use anyway.
CREATE INDEX licitacion_organo_id_idx ON licitacion (organo_id);
CREATE INDEX licitacion_state_id_idx ON licitacion (state_id);
CREATE INDEX licitacion_contract_type_id_idx ON licitacion (contract_type_id);
CREATE INDEX licitacion_procedure_type_id_idx ON licitacion (procedure_type_id);
CREATE INDEX licitacion_tramitacion_type_id_idx ON licitacion (tramitacion_type_id);

CREATE INDEX licitacion_cpv_lote_id_idx ON licitacion_cpv (lote_id);
CREATE INDEX licitacion_cpv_cpv_id_idx ON licitacion_cpv (cpv_id);

CREATE INDEX licitacion_nut_lote_id_idx ON licitacion_nut (lote_id);
CREATE INDEX licitacion_nut_nut_id_idx ON licitacion_nut (nut_id);

CREATE INDEX licitacion_award_lote_id_idx ON licitacion_award (lote_id);
CREATE INDEX licitacion_award_operador_economico_id_idx
    ON licitacion_award (operador_economico_id);

CREATE INDEX licitacion_formalisation_lote_id_idx ON licitacion_formalisation (lote_id);
