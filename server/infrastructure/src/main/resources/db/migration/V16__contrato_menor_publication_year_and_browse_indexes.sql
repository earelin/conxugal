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
-- column neither index carries. It also keeps anomalous rows out of both indexes entirely, so the
-- import maintains nothing for a contract nobody can browse to.
CREATE INDEX contrato_menor_organo_year_date_idx
    ON contrato_menor (organo_id, publication_year, publication_date, source_id)
    WHERE amount IS NOT NULL AND operador_economico_id IS NOT NULL;

CREATE INDEX contrato_menor_organo_year_amount_idx
    ON contrato_menor (organo_id, publication_year, amount, source_id)
    WHERE amount IS NOT NULL AND operador_economico_id IS NOT NULL;

-- Subsumed by the first index above, which leads with the same column and answers everything this
-- one did. A rebuild, not a third index.
DROP INDEX contrato_menor_organo_id_publication_date_idx;
