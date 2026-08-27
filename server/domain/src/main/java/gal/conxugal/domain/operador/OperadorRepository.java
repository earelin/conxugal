package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.Insert;
import java.util.Optional;
import java.util.Set;

/**
 * Port for maintaining the operadores económicos catalogue. Implemented by the
 * {@code infrastructure} module. The catalogue has one writer — the import that derives it from
 * the contracts it stores — so there is no create, rename or delete beyond what that needs.
 */
public interface OperadorRepository {

  /**
   * The operador holding the given fiscal identifier. The type is canonical, and the store holds
   * no other spelling to match against, so there is no query a caller could ask in the wrong
   * form.
   */
  Optional<OperadorEconomico> findByFiscalId(FiscalIdentifier fiscalId);

  /**
   * The operadores the catalogue holds under this name — the one each is displayed under, or any
   * one it has retained beside it. Identities rather than entries, because what the question is
   * really asking is <em>how many parties does this name reach</em>: two retained spellings of one
   * operador are one answer, and one name borne by two operadores is two.
   *
   * <p>It asks the catalogue and changes nothing: a name nothing has been catalogued under answers
   * an empty set rather than creating anything to answer with.
   */
  Set<OperadorId> findAllMatchingName(MatchableName name);

  @Insert
  OperadorEconomico insert(OperadorEconomico operador);

  /**
   * Moves the displayed name and the rank it was taken from, and stops retaining that name
   * beside the operador if it was retained. All of it or none of it: a row that took the name
   * without the rank would remember a name from one contract and a rank from another, and one
   * that took both without dropping the retained copy would hold its own displayed name as an
   * alternative — the state the aggregate refuses to be built in, and therefore one no later
   * read could load.
   *
   * <p>Dropping a name the operador never retained is not an error; a name published for the
   * first time simply has nothing to drop. Neither is promoting the name already displayed,
   * which advances the rank alone.
   *
   * <p>Touching two tables at once, the {@code infrastructure} implementation must write this
   * as explicit statements rather than a derived query.
   */
  void promoteName(OperadorId id, String name, NomeRank nameRank);

  /**
   * Retains a name the operador has been published under, either adding it or advancing the rank
   * of one already held. One operation, not find-then-write: the store decides which of the two
   * happened, so no caller can read, lose the race and insert a duplicate the store would then
   * reject. The {@code infrastructure} implementation needs an explicit upsert for that; no
   * derived query expresses it.
   */
  void retainName(NomeAlternativo nome);
}
