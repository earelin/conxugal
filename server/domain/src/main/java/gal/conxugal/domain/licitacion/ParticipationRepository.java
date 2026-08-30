package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.OperadorId;
import java.util.Collection;
import java.util.Optional;

/**
 * Port for a procedure's published bidders. Implemented by the {@code infrastructure} module.
 *
 * <p>No delete, on {@link LicitacionRepository}'s reasoning.
 */
public interface ParticipationRepository {

  /**
   * Stores one participation, matching it against what is held for <strong>the same bid</strong> —
   * its procedure, its lote and the operador it names — and refreshing it in place. Answers the
   * stored participation, carrying the identity assigned to it.
   *
   * <p>A <strong>null lote means the procedure as a whole</strong> and is part of the key, as is a
   * null operador, so the bidders of a lotless procedure match themselves across imports rather
   * than inserting afresh on every run.
   *
   * <p><strong>Two bidders of one award point that both resolved to nobody are stored as one row,
   * and the later write wins.</strong> Their key is identical — the procedure, the lote and a null
   * operador — so the second upsert conflicts with the first, answers its identity and overwrites
   * its {@code won} marker. The parse cannot prevent it: one row out per source row still yields
   * two values keyed alike. It is rare, since a bidder resolves to nobody only where its published
   * identifier was unusable and a consortium is catalogued rather than left null — but it loses a
   * published bid silently, so it is stated here rather than discovered.
   *
   * <p>The operador a participation names, where it names one, must already be stored — including
   * a consortium, which is catalogued before its bid is written.
   */
  Participation upsert(Participation participation);

  /**
   * The operador an <strong>identifier-less consortium</strong> of this procedure was catalogued
   * as, where an earlier import of it already minted one under this name.
   *
   * <p><strong>It exists because such an entry answers to nothing else.</strong> SPEC-0006 R3 keys
   * it on the bid it was published on, so it holds no fiscal identifier to be found by, and this
   * table's own key already contains the operador — which makes <em>the participation of this
   * bid</em> unaskable without the answer. Joining the two the other way round is what closes it:
   * this procedure's bids reach the catalogue entries they name, and among them at most one is a
   * consortium the source declined to identify under this name.
   *
   * <p><strong>The name is compared inside one procedure and nowhere else.</strong> That is what
   * keeps it clear of the continuity R3 refuses to claim — two procedures publishing a consortium
   * under the same name are two operadores, and this query cannot answer otherwise because it is
   * scoped by {@code licitacionId}. It is the same reduction the awardee routing compares on, so a
   * restated procedure that repunctuates a consortium's name still finds the entry it minted.
   *
   * <p><strong>A withdrawn bid still answers.</strong> A reconciliation that withdraws a
   * consortium's bid and then re-imports the procedure would otherwise mint a second entry beside
   * the one it had just marked, leaving the procedure holding its consortium twice.
   *
   * <p>Identified consortia are excluded: those are found by their fiscal identifier like any other
   * operador, and matching one here on a name would be the merge R3 forbids.
   */
  Optional<OperadorId> findConsortiumOperador(LicitacionId licitacionId, MatchableName name);

  /**
   * Marks withdrawn every bid of this procedure that {@code retained} does not name, on
   * {@link LoteRepository#withdrawAbsent}'s rule and for its reasons. Answers how many it newly
   * marked.
   *
   * <p>It sees a consortium's bid and a single firm's alike, so it is called once, after both have
   * been written — a consortium whose identity moved between imports loses its old bid here, beside
   * the ordinary bidder the record stopped naming.
   */
  int withdrawAbsent(LicitacionId licitacionId, Collection<ParticipationId> retained);

  /**
   * Sets each bid of this procedure to won or not, from the award points {@code winners} names.
   *
   * <p><strong>It only ever updates, and never writes a bid.</strong> An awardee that never bid is
   * the ordinary case rather than the exception — the bidder table is absent on most records, and a
   * name-derived awardee need not appear on this procedure at all — so upserting a winner would
   * invent a participation the source never published, inflate the procedure's bidder count, and
   * add a row on a re-import that is supposed to change nothing. A winner matching no bid marks
   * nothing, which is the honest outcome.
   *
   * <p><strong>Every bid of the procedure is written, not only the winners</strong>, so a bid that
   * stops winning is cleared by the same call. Withdrawn bids are included: one the source no
   * longer publishes must not stay marked as the winner of anything.
   *
   * <p>An award naming no operador marks nothing, and must: an award nothing resolved names nobody,
   * a bid whose identifier was unusable resolved to nobody, and two unidentified parties are not
   * evidence of one. A withdrawn award marks nothing either — it is no longer an award this
   * procedure makes.
   *
   * @param awards this procedure's awards, which is where the answer lives: which bid won is the
   *     resolution table's to say and the bidder table has no opinion on it
   */
  void markWinners(LicitacionId licitacionId, Collection<Award> awards);
}
