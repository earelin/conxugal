package gal.conxugal.domain.licitacion;

/**
 * Port for the CPV list — the European common procurement vocabulary a classification cites.
 * Implemented by the {@code infrastructure} module.
 *
 * <p><strong>Regulated is not the same as closed.</strong> CPV is versioned, and the 2008 revision
 * retired codes the 2003 one issued while this system imports procedures published across both, so
 * nothing seeds this list and nothing validates against it. An unseen code creates its row inside
 * the transaction that stores the procedure citing it, rather than failing a foreign key and
 * rejecting a procedure over a code that was perfectly good when it was published.
 *
 * <p>No delete, on {@link LicitacionRepository}'s reasoning: an entry no procedure cites any more
 * is still an entry the list assigns.
 */
public interface CpvRepository {

  /**
   * Stores the entry, matching it against what is held <strong>by its code</strong> — what the list
   * identifies an entry by — and answering the stored entry, whose identity a classification then
   * refers to.
   *
   * <p><strong>It does not write the description.</strong> An entry already held keeps whatever
   * wording is stored beside it, so an import carrying none — which is every import this feature
   * builds, since the record's CPV table publishes the code alone — cannot blank a description
   * something else supplied. The answer therefore carries the <em>stored</em> description, which
   * need not be the one passed in.
   *
   * <p>The only method here, and the absence of a finder beside it is deliberate: the upsert
   * answers the stored entry, so nothing storing a classification needs to look one up first.
   */
  Cpv upsert(Cpv cpv);
}
