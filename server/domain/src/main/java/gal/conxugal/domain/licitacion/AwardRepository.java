package gal.conxugal.domain.licitacion;

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
}
