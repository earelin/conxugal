package gal.conxugal.domain.licitacion;

/**
 * Port for the NUTS list — the European nomenclature of territorial units a classification cites.
 * Implemented by the {@code infrastructure} module.
 *
 * <p>Its own port rather than one shared with {@link CpvRepository}, so that a CPV entry cannot be
 * handed to it: the two are structurally identical and would otherwise be interchangeable at every
 * call site, which is exactly what the separate identifier types exist to prevent.
 *
 * <p>Like the CPV list it is neither seeded nor validated against — the nomenclature is revised
 * rather than closed, and this system imports procedures published across revisions — and there is
 * no delete: an entry no procedure cites any more is still an entry the list assigns.
 *
 * @see CpvRepository
 */
public interface NutRepository {

  /**
   * Stores the entry, matching it against what is held <strong>by its code</strong> and answering
   * the stored entry, whose identity a classification then refers to.
   *
   * <p><strong>It does not write the description</strong>, on {@link CpvRepository#upsert}'s
   * reasoning: an entry already held keeps the wording stored beside it, so the answer carries the
   * <em>stored</em> description rather than the one passed in.
   */
  Nut upsert(Nut nut);
}
