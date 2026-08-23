package gal.conxugal.domain.licitacion;

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
}
