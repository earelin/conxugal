-- Adding the column below rewrites the table under ACCESS EXCLUSIVE, and every reader and writer
-- queues behind that lock while it runs. Failing the boot is the better of the two bad outcomes:
-- waiting behind a long-running import would hold the whole service closed for as long as that
-- import takes, and with no bound.
SET LOCAL lock_timeout = '5s';

-- The cast is not decoration: EXTRACT answers numeric, and an INTEGER generated column will not
-- accept it without one. Generated is what makes the column unable to disagree with the date it
-- comes from, and what means no import writes it -- it is null exactly when publication_date is,
-- which is how an equality test on it withholds every undated contract without naming one.
ALTER TABLE contrato_menor
    ADD COLUMN publication_year INTEGER
        GENERATED ALWAYS AS (EXTRACT(YEAR FROM publication_date)::int) STORED;

-- Partial on the visibility rule, so each index holds only rows a reader can reach. Every browse
-- predicate matches this word for word, which is what lets the counts and the year facets answer
-- from the index rather than fetching each candidate row to re-test operador_economico_id -- a
-- column neither index carries. A contract missing its amount or its awardee is kept out
-- altogether; one missing only its date is carried, under a null year no browse predicate matches.
CREATE INDEX contrato_menor_organo_year_date_idx
    ON contrato_menor (organo_id, publication_year, publication_date, source_id)
    WHERE amount IS NOT NULL AND operador_economico_id IS NOT NULL;

CREATE INDEX contrato_menor_organo_year_amount_idx
    ON contrato_menor (organo_id, publication_year, amount, source_id)
    WHERE amount IS NOT NULL AND operador_economico_id IS NOT NULL;

-- Whole, because the read it serves is: the import's per-window completion check counts an
-- Órgano's contracts against the total the source publishes, so it counts the anomalous ones too.
-- Neither index above can answer that -- a count of every row cannot come from an index holding
-- only some -- and without this one it is a sequential scan of a table headed for millions, run
-- once per window of every walk.
CREATE INDEX contrato_menor_organo_id_idx ON contrato_menor (organo_id);

-- Subsumed by the three above: the browse reads take the first, the completion count takes the
-- last, and its publication_date column is what neither of them needed from it.
DROP INDEX contrato_menor_organo_id_publication_date_idx;
