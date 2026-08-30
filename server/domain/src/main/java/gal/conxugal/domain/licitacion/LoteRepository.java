package gal.conxugal.domain.licitacion;

import java.util.Collection;

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

  /**
   * Marks withdrawn every lote of this procedure that {@code retained} does not name — the ones a
   * restatement stopped publishing. Answers how many it newly marked.
   *
   * <p><strong>This is the reconciliation's whole shape, and every child port repeats it.</strong>
   * A restatement republishes the procedure in full, so what it no longer names is what the store
   * must stop showing; the caller hands back the identities its own upserts just answered with, and
   * everything else of this procedure goes. Nothing is compared field by field, because a
   * restatement is not a diff — it is the source's current statement, whole.
   *
   * <p><strong>An empty {@code retained} is ordinary rather than degenerate</strong>: it is a
   * procedure whose record now publishes no lote at all, and every lote it holds is withdrawn.
   *
   * <p>A lote already withdrawn is left alone, so a re-import that changes nothing writes nothing
   * here — which is what lets an unchanged restatement be observably free.
   *
   * <p>Still no delete: the row stays, and republishing it un-withdraws it through {@link #upsert}.
   */
  int withdrawAbsent(LicitacionId licitacionId, Collection<LoteId> retained);
}
