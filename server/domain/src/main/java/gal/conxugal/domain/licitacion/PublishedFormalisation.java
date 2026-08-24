package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One row of the record's formalisation table, as the source published it and before it becomes a
 * {@link Formalisation} — the signing of the contract, as opposed to the decision to award it.
 * Keyed on the award point like {@link PublishedAward}, with a null {@code loteKey} meaning
 * <em>the procedure as a whole</em>.
 *
 * <p><strong>This is where an awardee's fiscal identifier actually is.</strong> The resolution
 * table carried none over 119 measured award rows; this table's {@code Contratista} cell carries
 * the name and the identifier together, a UTE's own included, and it answers for 58% of all award
 * rows and 95% of those on a formalised procedure.
 *
 * <p><strong>The identifier is nullable because the split can decline.</strong> It takes a
 * trailing token shaped like a fiscal identifier; where the trailing token is not one, the whole
 * cell is the name and this is null. That row is still a valid formalisation — one route to an
 * identifier that did not answer, not a broken record — and it never reaches the outstanding
 * ledger.
 *
 * <p><strong>The contratista's name is kept apart from the award's {@code Adxudicatario} on
 * purpose.</strong> The two publications can name different parties, and where they do the
 * award's name governs; merging them here would destroy the distinction the rule rests on before
 * anything could apply it.
 *
 * <p>The table's {@code Data difusión} column is not read. There is nowhere on
 * {@link Formalisation} for it to go — unlike the classifications, which carry one — so requiring
 * or parsing it would refuse or discard a value with no destination.
 *
 * @param loteKey the award point this row names, reduced by {@link LoteKey}; null is the
 *     procedure as a whole
 * @param formalisationDate the published {@code Data formalización}
 * @param contratistaName the {@code Contratista} cell with any trailing identifier taken off it
 * @param fiscalIdentifier the identifier that trailing token carried, where it carried one
 * @param nationality the published {@code Nacionalidade}
 * @param amount the formalised {@code Importe}
 */
public record PublishedFormalisation(
    @Nullable String loteKey,
    @Nullable LocalDate formalisationDate,
    @Nullable String contratistaName,
    @Nullable FiscalIdentifier fiscalIdentifier,
    @Nullable String nationality,
    @Nullable Money amount) {

  /** Reduced on the way in, on {@link PublishedAward}'s reasoning. */
  public PublishedFormalisation {
    loteKey = LoteKey.normalise(loteKey).orElse(null);
    contratistaName = PublishedText.orNullWhenBlank(contratistaName);
    nationality = PublishedText.orNullWhenBlank(nationality);
  }
}
