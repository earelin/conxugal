package gal.conxugal.domain.licitacion;

import java.util.Optional;

/**
 * Port for the published tramitación-type vocabulary. Implemented by the {@code infrastructure}
 * module.
 *
 * @see LicitacionContractTypeRepository for why each type vocabulary has a port of its own, and
 *     why none of them is seeded, validated against or deletable
 */
public interface LicitacionTramitacionTypeRepository {

  /**
   * Stores the type, matching it against what is held by its name — the source's identifier for
   * this vocabulary — and answering the stored type, whose identity a procedure then refers to.
   */
  LicitacionTramitacionType upsert(LicitacionTramitacionType type);

  /** The stored type published under this name, or nothing. */
  Optional<LicitacionTramitacionType> findByName(String name);
}
