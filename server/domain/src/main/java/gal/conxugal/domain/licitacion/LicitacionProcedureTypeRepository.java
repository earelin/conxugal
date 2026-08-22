package gal.conxugal.domain.licitacion;

import java.util.Optional;

/**
 * Port for the published procedure-type vocabulary. Implemented by the {@code infrastructure}
 * module.
 *
 * @see LicitacionContractTypeRepository for why each type vocabulary has a port of its own, and
 *     why none of them is seeded, validated against or deletable
 */
public interface LicitacionProcedureTypeRepository {

  /**
   * Stores the type, matching it against what is held by its name — the source's identifier for
   * this vocabulary — and answering the stored type, whose identity a procedure then refers to.
   */
  LicitacionProcedureType upsert(LicitacionProcedureType type);

  /** The stored type published under this name, or nothing. */
  Optional<LicitacionProcedureType> findByName(String name);
}
