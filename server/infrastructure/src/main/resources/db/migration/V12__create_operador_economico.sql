CREATE TABLE operador_economico (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- Held canonical — trimmed and upper-cased — so that two published spellings differing
    -- only in padding or case cannot become two operadores. Uncollated: it is compared for
    -- equality, never ordered as Galician text.
    fiscal_id TEXT NOT NULL UNIQUE,
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
    -- One row per distinct name rather than per award: retention upserts on this key, so the
    -- largest operador holds one row per spelling instead of one per contract.
    CONSTRAINT operador_economico_nome_alternativo_key
        UNIQUE (operador_economico_id, name)
);
