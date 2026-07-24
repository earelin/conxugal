CREATE TABLE organo_contratacion (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    source_key VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
