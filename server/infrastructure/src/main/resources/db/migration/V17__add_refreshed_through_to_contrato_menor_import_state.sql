-- T₁: how far this Órgano has been *refreshed* through. Nullable because null is a fact rather
-- than a gap: the Órgano has never had a clean incremental run, and an incremental window's floor
-- falls back to covered_through for it. Added nullable so every existing row keeps working with
-- no backfill.
--
-- This makes four columns of Órgano fact, not the three V14's closing comment counts. That comment
-- is left as written: its checksum is recorded in every flyway_schema_history that has run it, and
-- editing an applied migration to correct a count fails validation on every such database.
ALTER TABLE contrato_menor_import_state
    ADD COLUMN refreshed_through TIMESTAMPTZ;
