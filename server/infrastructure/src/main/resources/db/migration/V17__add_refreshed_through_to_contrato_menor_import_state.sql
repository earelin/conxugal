-- T₁: how far this Órgano has been *refreshed* through. Nullable because null is a fact rather
-- than a gap: the Órgano has never had a clean incremental run, and an incremental window's floor
-- falls back to covered_through for it. Added nullable so every existing row keeps working with
-- no backfill.
ALTER TABLE contrato_menor_import_state
    ADD COLUMN refreshed_through TIMESTAMPTZ;
