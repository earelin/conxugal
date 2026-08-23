package gal.conxugal.domain.licitacion;

/**
 * Port for a procedure's lotes. Implemented by the {@code infrastructure} module.
 *
 * <p>No delete, on {@link LicitacionRepository}'s reasoning: a lote the source no longer publishes
 * for its procedure is retained and marked withdrawn, never erased.
 */
public interface LoteRepository {

  /**
   * Stores one lote, matching it against what is held for its procedure <strong>by the reduced form
   * of its identifier</strong> — {@link LoteKey}'s — and refreshing it in place. Answers the stored
   * lote, which carries the identity an award, a formalisation or a classification then hangs off.
   *
   * <p><strong>Matched on the reduced form, stored under the published one.</strong> A lote
   * published as {@code 05} is stored as {@code 05}, and a later row spelling it {@code 5} finds
   * that same lote rather than creating a second one beside it. That is measured rather than
   * defensive: the award table produced both {@code 1} and {@code 05} within one sample, so keying
   * on the published spelling would split one lote in two on a procedure the source publishes
   * perfectly well.
   *
   * <p><strong>A lote already stored keeps the spelling it is stored under</strong>, and the answer
   * carries that rather than the one passed in. The two source tables that name a lote do not agree
   * on how to spell one, so neither spelling is the more published; taking the later one would make
   * what a reader is shown depend on the order its caller wrote in.
   *
   * <p>The procedure must already be stored, carrying the identity its own upsert answered with.
   */
  Lote upsert(Lote lote);
}
