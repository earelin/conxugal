package gal.conxugal.domain.licitacion;

import java.util.Collection;

/**
 * Port for a procedure's CPV classifications. Implemented by the {@code infrastructure} module.
 *
 * <p>No delete, on {@link LicitacionRepository}'s reasoning: a classification the source no longer
 * publishes is retained and marked withdrawn.
 */
public interface CpvClassificationRepository {

  /**
   * Stores one classification, matching it against what is held by <strong>its procedure, its lote
   * and the entry it cites</strong>, and refreshing it in place. Answers the stored classification,
   * carrying the identity assigned to it.
   *
   * <p>A <strong>null lote means the procedure as a whole</strong> and is part of the key rather
   * than a gap in it, so a procedure-wide row matches itself across imports instead of inserting
   * afresh on every run. It is the ordinary case: most procedures have no lotes at all, and even
   * one that has them may publish its classification procedure-wide.
   *
   * <p><strong>The CPV entry must already be stored</strong>, carrying the identity
   * {@link CpvRepository#upsert} answered with. Nothing in the domain enforces the ordering — a
   * classification constructs perfectly well around an entry the database has never seen — so an
   * implementation must refuse an unstored one rather than write a null into a {@code NOT NULL}
   * foreign key, whose error would name the column rather than the mistake. The same holds for the
   * procedure the classification belongs to.
   */
  CpvClassification upsert(CpvClassification classification);

  /**
   * Marks withdrawn every classification of this procedure that {@code retained} does not name, on
   * {@link LoteRepository#withdrawAbsent}'s rule and for its reasons. Answers how many it newly
   * marked.
   */
  int withdrawAbsent(LicitacionId licitacionId, Collection<CpvClassificationId> retained);
}
