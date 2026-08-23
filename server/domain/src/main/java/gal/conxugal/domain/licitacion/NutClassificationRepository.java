package gal.conxugal.domain.licitacion;

/**
 * Port for a procedure's NUTS classifications. Implemented by the {@code infrastructure} module.
 *
 * <p>Its own port rather than one shared with {@link CpvClassificationRepository}: the two map two
 * tables and cite two different lists, and only the compiler stops a NUTS row reaching the CPV one.
 *
 * @see CpvClassificationRepository
 */
public interface NutClassificationRepository {

  /**
   * Stores one classification, matching it against what is held by <strong>its procedure, its lote
   * and the entry it cites</strong>, and refreshing it in place. A null lote means the procedure as
   * a whole and is part of the key rather than a gap in it.
   *
   * <p><strong>The NUTS entry must already be stored</strong>, carrying the identity
   * {@link NutRepository#upsert} answered with; an implementation refuses an unstored one rather
   * than writing a null into a {@code NOT NULL} foreign key.
   */
  NutClassification upsert(NutClassification classification);
}
