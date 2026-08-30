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
   *
   * <p><strong>An operador holding no fiscal identifier is never among them</strong>, and the
   * exclusion is the store's rather than each caller's. SPEC-0006 R3 admits one such party — a UTE
   * the source declines to identify, catalogued per bid — and states its safety as an absolute:
   * <em>"an entry is never found by anything but a fiscal identifier, so no identifier-less entry
   * can absorb a contract belonging to another party"</em>. ADR-0023 rests on the same sentence.
   * This is the one query that could break it: a name match reaching such an entry would attribute
   * a second procedure's award to the first procedure's consortium, silently and plausibly. R8's
   * user-facing lookup <em>does</em> find them by name, which is a different question asked by a
   * reader rather than by the derivation, and it is not this method.
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
   * Marks an operador already in the catalogue as a consortium. Idempotent, and one-way: the
   * source publishes being a UTE structurally and never publishes the absence of it, so nothing
   * here unmarks.
   *
   * <p><strong>It exists because a UTE can be catalogued by a family that knows nothing of
   * consortia.</strong> A consortium holding an ordinary fiscal identifier is an ordinary operador
   * to the contratos menores import, which stores it unmarked — and that family imports first
   * for a newly marked Órgano — so without this a UTE first named by a contrato menor
   * would stay an ordinary firm however many licitacións published it as a consortium.
   *
   * <p>The marker is the only thing it writes: the name, the rank and the retained set are the
   * name rule's, and being published as a consortium says nothing about any of them.
   */
  void markAsUte(OperadorId id);

  /**
   * Retains a name the operador has been published under, either adding it or advancing the rank
   * of one already held. One operation, not find-then-write: the store decides which of the two
   * happened, so no caller can read, lose the race and insert a duplicate the store would then
   * reject. The {@code infrastructure} implementation needs an explicit upsert for that; no
   * derived query expresses it.
   */
  void retainName(NomeAlternativo nome);
}
