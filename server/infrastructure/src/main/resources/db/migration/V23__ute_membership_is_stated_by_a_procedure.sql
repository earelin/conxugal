-- A membership becomes a statement by one procedure, rather than a bare fact about two operadores.
--
-- V21 keyed it on the pair alone, which is enough while a consortium is catalogued per bid: an
-- unidentified UTE is reachable from one procedure and nowhere else, so withdrawing that bid can
-- withdraw everything under it. It is not enough for a UTE the source identifies, which is one
-- operador across every procedure naming it -- 2 of 35 measured. Two such procedures publish
-- different member lists, and when the first stops stating a member the pair alone cannot say
-- whether the second still does. Withdrawing it would hide a fact the second publishes; keeping it
-- would show one nothing publishes. The procedure that states the membership is the missing column,
-- and with it the reconciliation withdraws its own statement and reads the pair as visible while
-- any procedure's statement of it is.
--
-- NOT NULL with no default and no backfill: nothing writes this table yet -- the cataloguing that
-- does has no production caller -- so it is empty in every environment, including the local seed.
ALTER TABLE operador_ute_membership
    ADD COLUMN licitacion_id UUID NOT NULL
        CONSTRAINT operador_ute_membership_licitacion_id_fkey REFERENCES licitacion (id);

ALTER TABLE operador_ute_membership DROP CONSTRAINT operador_ute_membership_pkey;

ALTER TABLE operador_ute_membership
    ADD CONSTRAINT operador_ute_membership_pkey
    PRIMARY KEY (ute_id, operador_economico_id, licitacion_id);

-- The reconciliation reads this table by the procedure it is about to restate, which the primary
-- key cannot serve: that key leads with the UTE. The member's own index V21 created still serves
-- the read from the firm's end.
CREATE INDEX operador_ute_membership_licitacion_id_idx
    ON operador_ute_membership (licitacion_id);
