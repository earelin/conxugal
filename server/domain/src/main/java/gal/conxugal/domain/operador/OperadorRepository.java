package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.util.Optional;

/**
 * Port for maintaining the operadores económicos catalogue. Implemented by the
 * {@code infrastructure} module. The catalogue has one writer — the import that derives it from
 * the contracts it stores — so there is no create, rename or delete beyond what that needs.
 */
public interface OperadorRepository {

  /**
   * The operador holding the given fiscal identifier, which is already in its canonical form:
   * the store holds no other spelling to match against.
   */
  Optional<OperadorEconomico> findByFiscalId(String fiscalId);

  @Insert
  OperadorEconomico insert(OperadorEconomico operador);

  /**
   * Moves the displayed name and the rank it was taken from in one write. They are one operation
   * rather than two because a row that took one without the other would remember a name from one
   * contract and a rank from another.
   */
  void updateNameAndNameRank(@Id OperadorId id, String name, NomeRank nameRank);

  /**
   * Retains a name the operador has been published under, either adding it or advancing the rank
   * of one already held. One operation, not find-then-write: the store decides which of the two
   * happened, so no caller can read, lose the race and insert a duplicate the store would then
   * reject.
   */
  void retainName(NomeAlternativo nome);
}
