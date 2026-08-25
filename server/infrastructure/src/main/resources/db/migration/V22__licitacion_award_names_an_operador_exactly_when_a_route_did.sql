-- An award names an operador exactly when a route reached one. The domain record refuses both
-- incoherent shapes -- a null operador beside a route that answered, and an operador beside
-- UNRESOLVED -- and this is the guarantee behind it, on the precedent of the fiscal identifier
-- check beside the UTE marker.
--
-- It is here rather than left to the record because the harm is asymmetric. Nothing writes such a
-- row today, but the record is what every future read maps into, and one row the aggregate refuses
-- to be built from makes its whole procedure unreadable through the very read a later import
-- performs -- with no delete on the port to recover it. A row refused at the insert is a failure
-- where the mistake is.
--
-- No route can answer without an operador: SPEC-0006 R3's resolution catalogues an identifier
-- nothing named before rather than failing to find it, and the one route that infers declines by
-- reaching nobody and leaving the path UNRESOLVED. So this is total rather than a rule with a
-- legitimate exception waiting to be discovered.
ALTER TABLE licitacion_award
    ADD CONSTRAINT licitacion_award_awardee_resolution_check
    CHECK ((operador_economico_id IS NULL) = (awardee_resolution_path = 'UNRESOLVED'));
