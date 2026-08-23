package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the source published in the formalisation table for one award point — the signing of the
 * contract, as opposed to the decision to award it. {@code id} is a system-assigned identity,
 * {@code null} only until the database assigns it.
 *
 * <p><strong>Its own record rather than a few more components on {@link Award}</strong>, because
 * the two are separate publications that can disagree. A procedure can be awarded without being
 * formalised — of 284 measured award rows, 112 sat on procedures in a state that has no
 * formalisation — and the two can name different parties, which a later task has a rule for.
 * Folding them together would force one row to stand for two facts the source states apart.
 *
 * <p><strong>This is where an awardee's fiscal identifier actually is.</strong> The resolution
 * table carried none over 119 measured award rows; the formalisation's {@code Contratista} cell
 * carries the name and the identifier together — {@code EQUINSE, S.A. A41111220} — and is the
 * route that resolves 58% of awards. The identifier is nullable because the split takes a trailing
 * token that is shaped like one, and a cell whose trailing token is not yields the name alone
 * rather than a rejected row.
 *
 * <p>Every published value is nullable and null means the source published nothing there; a value
 * that could not be interpreted arrives here as null and is not a reason to refuse the row. Text
 * that carried only whitespace is held as null rather than as an empty string. The procedure
 * reference is required on every row and a null lote means <em>the procedure as a whole</em>.
 *
 * <p>{@code withdrawn} is the marker R13's retention needs. Nothing here writes it — the
 * reconciliation that does is a later task's.
 */
@MappedEntity("licitacion_formalisation")
public record Formalisation(
    @Id @GeneratedValue @Nullable FormalisationId id,
    LicitacionId licitacionId,
    @Nullable LoteId loteId,
    @Nullable LocalDate formalisationDate,
    @Nullable String contratistaName,
    @Nullable FiscalIdentifier fiscalIdentifier,
    @Nullable String nationality,
    @Nullable Money amount,
    boolean withdrawn) {

  public Formalisation {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    contratistaName = PublishedText.orNullWhenBlank(contratistaName);
    nationality = PublishedText.orNullWhenBlank(nationality);
  }

  /**
   * A formalisation as it is first read from the source: the database assigns its id on insert,
   * and nothing an import does withdraws it.
   */
  public Formalisation(
      LicitacionId licitacionId,
      @Nullable LoteId loteId,
      @Nullable LocalDate formalisationDate,
      @Nullable String contratistaName,
      @Nullable FiscalIdentifier fiscalIdentifier,
      @Nullable String nationality,
      @Nullable Money amount) {
    this(
        null,
        licitacionId,
        loteId,
        formalisationDate,
        contratistaName,
        fiscalIdentifier,
        nationality,
        amount,
        false);
  }

  /**
   * Identity, not contents: two instances are the same formalisation when they carry the same
   * assigned {@link FormalisationId}. A formalisation the source restates — a corrected amount, an
   * identifier filled in — is the same formalisation, and the record's own equality would call it
   * a different one.
   *
   * <p>A formalisation the database has not assigned an identity to is equal only to itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Formalisation formalisation
        && id != null
        && id.equals(formalisation.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
