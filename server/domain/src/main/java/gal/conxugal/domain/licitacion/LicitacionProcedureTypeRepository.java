package gal.conxugal.domain.licitacion;

/**
 * Port for the published procedure-type vocabulary. Implemented by the {@code infrastructure}
 * module.
 *
 * @see LicitacionContractTypeRepository for why each type vocabulary has a port of its own, why
 *     none of them is seeded, validated against or deletable, and why none carries a finder
 */
public interface LicitacionProcedureTypeRepository {

  /**
   * Stores the type, matching it against what is held by its name — the source's identifier for
   * this vocabulary — and answering the stored type, whose identity a procedure then refers to.
   */
  LicitacionProcedureType upsert(LicitacionProcedureType type);
}
