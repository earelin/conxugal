/**
 * Licitacións: the {@link gal.conxugal.domain.licitacion.Licitacion} aggregate a stored tender
 * procedure is, its {@link gal.conxugal.domain.licitacion.LicitacionId} identity, the
 * {@link gal.conxugal.domain.licitacion.PublicationId} the source published it under — a type of
 * its own so the two cannot be confused, since they identify one procedure and mean opposite
 * things — and the
 * {@link gal.conxugal.domain.licitacion.LicitacionRepository} port that stores them — matching on
 * the source's publication identifier and answering an
 * {@link gal.conxugal.domain.licitacion.UpsertOutcome} that says which branch the write took.
 *
 * <p>Beside the aggregate sit the four vocabularies it refers to, each a row of its own so a value
 * the source publishes on a thousand procedures is held once: the
 * {@link gal.conxugal.domain.licitacion.LicitacionState}, keyed on the {@code estado} code the
 * source publishes and carrying a label two codes are allowed to share, and the three types a
 * record publishes as a bare name — {@link gal.conxugal.domain.licitacion.LicitacionContractType},
 * {@link gal.conxugal.domain.licitacion.LicitacionProcedureType} and
 * {@link gal.conxugal.domain.licitacion.LicitacionTramitacionType}. Each has an identifier type
 * and a port of its own, so the compiler refuses a procedure type where a contract type belongs,
 * and none of them is seeded or validated against: a value the source has not published before
 * simply creates its row.
 *
 * <p>Under the procedure sit its <strong>award points</strong> — a
 * {@link gal.conxugal.domain.licitacion.Lote} where it has lotes and the procedure itself where it
 * does not, which is why the lote reference is nullable on every row that carries one and a null
 * one reads as <em>the procedure as a whole</em> rather than as <em>unattached</em>. Each award
 * point carries its {@link gal.conxugal.domain.licitacion.CpvClassification} and
 * {@link gal.conxugal.domain.licitacion.NutClassification} — two records because they map two
 * tables, and either may hang off the procedure even where lotes exist, because that is what the
 * source publishes. Each one <em>refers</em> to its entry rather than copying it:
 * {@link gal.conxugal.domain.licitacion.Cpv} and {@link gal.conxugal.domain.licitacion.Nut} are
 * regulated European lists rather than this source's own vocabulary, so an entry thousands of
 * procedures cite is held once, matched on the code the list assigns and never on its wording, and
 * unseeded because a regulated list is versioned rather than closed. Beside them sit its
 * {@link gal.conxugal.domain.licitacion.Award}, its
 * {@link gal.conxugal.domain.licitacion.Formalisation}, and the
 * {@link gal.conxugal.domain.licitacion.Participation} of each published bidder. One place per
 * thing awarded, with no second copy at procedure level; the model makes both expressible and the
 * parse is what keeps the invariant.
 *
 * <p>An award records
 * {@link gal.conxugal.domain.licitacion.AwardeeResolutionPath how its operador was reached}, a
 * totally ordered vocabulary so a published identifier is known to supersede a derived one. A
 * participation names its party by reference and holds no name of its own — a consortium is an
 * operador like any other bidder, so UTE membership relates two catalogue entries and lives in
 * {@link gal.conxugal.domain.operador} rather than here. And
 * {@link gal.conxugal.domain.licitacion.LoteKey} is the one reduction every lote cell passes
 * through before anything is compared against it — the four tables that carry a lote column do not
 * spell one the same way.
 *
 * <p>Retrieval enters through
 * {@link gal.conxugal.domain.licitacion.LicitacionListingSource}, which answers one
 * {@link gal.conxugal.domain.licitacion.LicitacionListingPage} of an Órgano's published history —
 * its {@link gal.conxugal.domain.licitacion.LicitacionListingEntry entries} and the Órgano's own
 * count — in an explicitly asked-for
 * {@link gal.conxugal.domain.licitacion.LicitacionListingOrder}, or throws
 * {@link gal.conxugal.domain.licitacion.LicitacionListingUnavailableException} rather than let a
 * walk mistake a failure for the end of a history. It is the cheap half of a two-mechanism
 * retrieval, and its entry is not a whole procedure — but it is the only place four of the
 * aggregate's values are published, which is why the listing is read even when the record is
 * already in hand.
 *
 * <p><strong>One procedure</strong> enters through
 * {@link gal.conxugal.domain.licitacion.LicitacionRecordSource}, which answers the
 * {@link gal.conxugal.domain.licitacion.LicitacionRecord} its published page states — or throws
 * {@link gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException} rather than hand back
 * a half-built procedure, which would store as authoritative with nothing ever returning to it.
 * That is the expensive half of the same two-mechanism retrieval: one call per stored procedure
 * against the listing's hundred, which is why they are two ports and not one. It answers a source
 * record rather than a {@link gal.conxugal.domain.licitacion.Licitacion} because the aggregate
 * needs values only the listing publishes. Its two economic figures arrive as a
 * {@link gal.conxugal.domain.licitacion.PublishedAmount}, which carries the
 * {@link gal.conxugal.domain.licitacion.VatBasis} the source labelled each with — measured to vary
 * between procedures, so it is read rather than assumed.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.licitacion;

import org.jspecify.annotations.NullMarked;
