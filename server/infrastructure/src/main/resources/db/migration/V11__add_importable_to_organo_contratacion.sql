ALTER TABLE organo_contratacion
    -- The default is what lets this column be NOT NULL over an already-populated catalogue,
    -- and what an instance running the previous insert would leave behind mid-deploy. Every
    -- Órgano therefore arrives unmarked, and importing is opted into deliberately.
    ADD COLUMN importable BOOLEAN NOT NULL DEFAULT FALSE;
