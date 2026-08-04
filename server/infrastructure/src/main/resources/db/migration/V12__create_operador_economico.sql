CREATE TABLE operador_economico (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- Held canonical — trimmed and upper-cased — so that two published spellings differing
    -- only in padding or case cannot become two operadores. Uncollated: where it is ordered it
    -- is ordered as an opaque key, not as Galician text.
    fiscal_id VARCHAR(16) NOT NULL UNIQUE,
    -- Collated on the column like termo.name, so every ORDER BY name a repository derives
    -- inherits it rather than each query having to remember an explicit COLLATE.
    name TEXT COLLATE "galician" NOT NULL,
    name_rank_date DATE,
    name_rank_source_id BIGINT NOT NULL
);

CREATE TABLE operador_economico_nome_alternativo (
    operador_economico_id UUID NOT NULL
        CONSTRAINT operador_economico_nome_alternativo_operador_fkey
            REFERENCES operador_economico (id),
    name TEXT COLLATE "galician" NOT NULL,
    last_published_date DATE,
    last_published_source_id BIGINT NOT NULL,
    -- The operador and the name are the whole key: one row per distinct name rather than per
    -- award, so the largest operador holds one row per spelling instead of one per contract,
    -- and retention upserts on it rather than inserting a duplicate.
    CONSTRAINT operador_economico_nome_alternativo_pkey
        PRIMARY KEY (operador_economico_id, name)
);
