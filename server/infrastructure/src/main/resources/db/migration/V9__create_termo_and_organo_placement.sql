-- Named so callers reference it by name rather than repeating the locale string; the
-- default C/POSIX collation sorts accented Galician names after Z.
CREATE COLLATION galician (provider = icu, locale = 'gl-ES');

CREATE TABLE termo (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    -- No ON DELETE action: CASCADE would let one delete remove a whole subtree,
    -- defeating the rule that a delete is rejected while the term has children.
    parent_id UUID REFERENCES termo (id)
);

-- Enforces the sibling-name rule against a concurrent create. NULLS NOT DISTINCT
-- extends it to roots, whose parent_id is null; lower(name) makes it case-insensitive.
-- Explicitly COLLATE galician so the case-folding doesn't depend on the cluster's
-- default locale, same as the ordering this collation was declared for.
CREATE UNIQUE INDEX termo_sibling_name_key
    ON termo (parent_id, lower(name COLLATE "galician")) NULLS NOT DISTINCT;

ALTER TABLE organo_contratacion
    -- No ON DELETE action: DeleteTermo clears placements itself in the same
    -- transaction, and the bare foreign key is the backstop against a skipped clear.
    ADD COLUMN termo_id UUID REFERENCES termo (id);

-- Postgres does not auto-index foreign key columns; without this, clearing every
-- placement for a deleted term and the FK's own referential check both scan the
-- whole catalogue while the taxonomy advisory lock is held.
CREATE INDEX organo_contratacion_termo_id_idx ON organo_contratacion (termo_id);
