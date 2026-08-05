CREATE TABLE contrato_menor (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- The source's own identifier, unique here so "no duplicates" holds at the store even when
    -- use-case logic slips, exactly as source_key does for the catalogue.
    source_id BIGINT NOT NULL UNIQUE,
    organo_id UUID NOT NULL
        CONSTRAINT contrato_menor_organo_id_fkey REFERENCES organo_contratacion (id),
    -- The only date column: the source's DD-MM-YYYY text is interpreted at the adapter and the
    -- text it arrived as is not retained.
    publication_date DATE,
    obxecto TEXT,
    amount NUMERIC,
    -- The adapter caps the duration in Java first, so an unexpectedly long one loses its tail
    -- rather than failing a batch and rejecting a real award. This bound is the backstop that
    -- keeps that cap honest, not the thing enforcing it.
    duration VARCHAR(64),
    -- This column is the awardee: the name and fiscal identifier live once on operador_economico
    -- and no contract row repeats them. Nullable because an award whose identifier is unusable
    -- records no awardee at all, and because nothing writes it until the operador derivation
    -- lands.
    operador_economico_id UUID
        CONSTRAINT contrato_menor_operador_economico_id_fkey REFERENCES operador_economico (id)
);

-- Whole rather than partial, though the column holds only nulls until the operador derivation
-- lands and a partial index would serve every read that ever matches. Kept whole because the
-- foreign key's own checks are what run against it in the meantime, and narrowing it later costs
-- one index rebuild rather than the backfill that adding the column later would have cost.
CREATE INDEX contrato_menor_operador_economico_id_idx ON contrato_menor (operador_economico_id);

-- Created here, empty, because adding it later to a table of millions is a different operation
-- from creating it now. It is what the browsing feature's mandatory year scoping reads on.
CREATE INDEX contrato_menor_organo_id_publication_date_idx
    ON contrato_menor (organo_id, publication_date);
