ALTER TABLE organo_contratacion
    DROP CONSTRAINT organo_contratacion_termo_id_fkey;

ALTER TABLE organo_contratacion
    ADD CONSTRAINT organo_contratacion_termo_id_fkey
        FOREIGN KEY (termo_id) REFERENCES termo (id) ON DELETE SET NULL;
