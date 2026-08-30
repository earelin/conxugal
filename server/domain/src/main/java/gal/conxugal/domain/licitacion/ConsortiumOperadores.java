package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.OperadorId;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Which operador each consortium of one procedure was catalogued as, keyed on the name the source
 * published it under.
 *
 * <p>It exists so that the award resolution does not have to decide the question a second time.
 * Whether a consortium is identified is a property of the <strong>procedure</strong> — its bidder
 * row and its formalisation are both allowed to supply the identifier — and cataloguing it happens
 * before any award is written, so what an award needs is the answer rather than the evidence.
 * Deriving it again per award row is how a bid and the award it won end up pointing at two
 * different operadores, which is exactly what SPEC-0006 #40 forbids.
 *
 * <p><strong>The key is the matchable name, not the published one.</strong> The award table names
 * its awardee in text and the bidder table names the consortium in text, and the two are compared
 * on the same fold everything else in this package compares names on. Nothing folded is stored:
 * the consortium's own name lives on its operador exactly as published.
 *
 * <p><strong>The fiscal identifier is carried, and its being null is the whole
 * distinction.</strong> An identified consortium is an ordinary awardee — its award resolves
 * through the catalogue and ranks its name like any other contract's. An unidentified one has
 * nothing to resolve through, so its award links to the operador its bid minted and ranks
 * nothing, which is why that operador keeps the one name its bid published.
 */
public record ConsortiumOperadores(Map<MatchableName, CataloguedConsortium> byName) {

  public ConsortiumOperadores {
    Objects.requireNonNull(byName, "byName must not be null");
    byName = Map.copyOf(byName);
  }

  /**
   * A procedure whose consortia have not been catalogued — which is every procedure that published
   * none, and also the shape a caller passes when it stores awards without them. An award whose
   * awardee is a consortium then reaches no route at all and names nobody, rather than being
   * attributed by the catalogue match to whichever firm happens to answer to a consortium's name.
   */
  public static ConsortiumOperadores none() {
    return new ConsortiumOperadores(Map.of());
  }

  /** The consortium this awardee name is, or null where the procedure published none such. */
  public @Nullable CataloguedConsortium at(MatchableName name) {
    return byName.get(name);
  }

  /**
   * One catalogued consortium: the operador it is, the identifier it holds where the procedure
   * published one anywhere, and which publication supplied that identifier.
   *
   * @param operadorId the catalogue entry, always present — a consortium is an operador either way
   * @param fiscalId the consortium's own identifier, or null for the 33-of-35 case
   * @param path which publication reached it, recorded on any award attributed to it so that a
   *     later restatement can let a published identifier supersede a derived one
   */
  public record CataloguedConsortium(
      OperadorId operadorId, @Nullable FiscalIdentifier fiscalId, AwardeeResolutionPath path) {

    public CataloguedConsortium {
      Objects.requireNonNull(operadorId, "operadorId must not be null");
      Objects.requireNonNull(path, "path must not be null");
      if (path == AwardeeResolutionPath.UNRESOLVED) {
        throw new IllegalArgumentException(
            "a catalogued consortium is reached by a published route: " + operadorId);
      }
    }
  }
}
