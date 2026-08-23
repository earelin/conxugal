package gal.conxugal.domain.licitacion;

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
}
