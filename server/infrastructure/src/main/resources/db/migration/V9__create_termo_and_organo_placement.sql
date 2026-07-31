-- Named so callers reference it by name rather than repeating the locale string; the
-- default C/POSIX collation sorts accented Galician names after Z.
CREATE COLLATION galician (provider = icu, locale = 'gl-ES');

CREATE TABLE termo (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- Collated on the column so every ORDER BY name a repository derives inherits it,
    -- rather than each query having to remember an explicit COLLATE.
    name VARCHAR(255) COLLATE "galician" NOT NULL,
    -- No ON DELETE action: CASCADE would let one delete remove a whole subtree,
    -- defeating the rule that a delete is rejected while the term has children.
    parent_id UUID CONSTRAINT termo_parent_id_fkey REFERENCES termo (id),
    -- The one cycle a serialising lock cannot catch: it needs no second row, so there is
    -- no concurrent window for the use case's own check to miss.
    CONSTRAINT termo_parent_not_self CHECK (parent_id IS DISTINCT FROM id)
);

-- Enforces the sibling-name rule against a concurrent create. NULLS NOT DISTINCT
-- extends it to roots, whose parent_id is null; lower(name) makes it case-insensitive,
-- folding case under the column's collation rather than the cluster's default locale.
CREATE UNIQUE INDEX termo_sibling_name_key
    ON termo (parent_id, lower(name)) NULLS NOT DISTINCT;

ALTER TABLE organo_contratacion
    -- No ON DELETE action: DeleteTermo clears placements itself in the same
    -- transaction, and the bare foreign key is the backstop against a skipped clear.
    ADD COLUMN termo_id UUID
        CONSTRAINT organo_contratacion_termo_id_fkey REFERENCES termo (id);

-- The catalogue read orders by this column, so it needs the same collation as termo.name;
-- V6 predates the collation and left it on the cluster default.
ALTER TABLE organo_contratacion
    ALTER COLUMN name TYPE VARCHAR(255) COLLATE "galician";

-- Postgres does not auto-index foreign key columns; without this, clearing every
-- placement for a deleted term and the FK's own referential check both scan the
-- whole catalogue while the taxonomy advisory lock is held.
CREATE INDEX organo_contratacion_termo_id_idx ON organo_contratacion (termo_id);
