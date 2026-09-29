package gal.conxugal.domain.licitacion;

import java.util.Collection;
import java.util.List;

/**
 * Port for a procedure's awards. Implemented by the {@code infrastructure} module.
 *
 * <p>No delete, on {@link LicitacionRepository}'s reasoning.
 */
public interface AwardRepository {

  /**
   * Stores one award, matching it against what is held by <strong>its procedure and its
   * lote</strong> — the award point it belongs to — and refreshing it in place. Answers the stored
   * award, carrying the identity assigned to it.
   *
   * <p>A <strong>null lote means the procedure as a whole</strong> and is part of the key, so the
   * one award of a lotless procedure — 85 of 100 measured — matches itself across imports rather
   * than inserting a second row on every run.
   *
   * <p><strong>One award per award point is the parse's guarantee, not this port's.</strong> The
   * key bounds a procedure to one procedure-level award and one per lote; nothing here prevents
   * both existing at once, which is what makes a procedure-level row and per-lote rows
   * coexpressible in the first place. What keeps the invariant is one row out per source row.
   *
   * <p>The operador an award names, where it names one, must already be stored.
   */
  Award upsert(Award award);

  /**
   * Every award this procedure holds, withdrawn ones included, in no guaranteed order.
   *
   * <p><strong>Withdrawn ones included, and that is the point.</strong> This is what a restatement
   * compares its freshly resolved awardee against, and the comparison is on the route that reached
   * the stored link rather than on whether the row is currently shown. An award withdrawn by an
   * earlier restatement and published again still carries the identifier its formalisation once
   * gave it, and hiding it here would let a name-derived link overwrite a published one the moment
   * the source republished the row.
   */
  List<Award> findAllByLicitacionId(LicitacionId licitacionId);

  /**
   * Marks withdrawn every award of this procedure that {@code retained} does not name, on
   * {@link LoteRepository#withdrawAbsent}'s rule and for its reasons. Answers how many it newly
   * marked.
   */
  int withdrawAbsent(LicitacionId licitacionId, Collection<AwardId> retained);
}
