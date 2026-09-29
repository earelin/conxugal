package gal.conxugal.domain.licitacion;

import java.util.Collection;

/**
 * Port for a procedure's formalisations. Implemented by the {@code infrastructure} module.
 *
 * <p>Its own port rather than one folded into {@link AwardRepository}, because the two are separate
 * publications that can disagree: a procedure can be awarded without being formalised, and the two
 * can name different parties.
 *
 * <p>No delete, on {@link LicitacionRepository}'s reasoning.
 */
public interface FormalisationRepository {

  /**
   * Stores one formalisation, matching it against what is held by <strong>its procedure and its
   * lote</strong> and refreshing it in place. Answers the stored formalisation, carrying the
   * identity assigned to it. A null lote means the procedure as a whole and is part of the key.
   */
  Formalisation upsert(Formalisation formalisation);

  /**
   * Marks withdrawn every formalisation of this procedure that {@code retained} does not name, on
   * {@link LoteRepository#withdrawAbsent}'s rule and for its reasons. Answers how many it newly
   * marked.
   *
   * <p>It is the row whose disappearance matters most, and the reason the awardee link is gated
   * rather than simply re-derived: a formalisation withdrawn here would otherwise demote the award
   * it published from {@code PUBLISHED_BY_FORMALISATION} to a derived link or to nothing.
   */
  int withdrawAbsent(LicitacionId licitacionId, Collection<FormalisationId> retained);
}
