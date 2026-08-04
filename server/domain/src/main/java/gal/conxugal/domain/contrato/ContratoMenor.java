package gal.conxugal.domain.contrato;

import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.OperadorEconomico;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A contrato menor as the source published it. {@code id} is a system-assigned identity,
 * {@code null} only until the database assigns it; {@code sourceId} is the source's own
 * identifier, which is what matches this contract across imports and what makes a re-import an
 * update in place rather than a duplicate.
 *
 * <p>Only the identity is required: the source identifier and the awarding Órgano. Every other
 * value is nullable, and null means <em>the source published nothing there</em> — a required
 * field anywhere else would reject a real award over a blank the source left. A publication date
 * that could not be interpreted and an amount that was absent both arrive here as null, and
 * neither is a reason to refuse the contract.
 *
 * <p>Nothing published is altered on the way in. Text arrives already stripped of the padding the
 * source uses to fill its fixed-width fields, and the duration arrives already capped, both done
 * at the adapter; this aggregate stores what it is handed — no case folding, no collapsing of
 * internal spacing, no rounding, no truncation of its own. A value that carried only whitespace
 * is held as null rather than as an empty string, because it published nothing and null is
 * already this aggregate's word for that.
 *
 * <p>The publication date is stored interpreted and only interpreted: the {@code DD-MM-YYYY} text
 * it arrived as is parsed at the adapter and not retained. The amount needs no such pair — the
 * source publishes it as a number, so there is no published spelling to lose — and it is
 * VAT-inclusive, which whatever displays it or a total derived from it has to say.
 *
 * <p>The awardee is a reference, not columns here: the published name and fiscal identifier live
 * once, on the {@link OperadorEconomico} row. It is nullable because an award whose identifier is
 * unusable is stored under no operador rather than an invented one, and because nothing populates
 * it until the operador derivation lands — so a contract holding null there is a valid contract
 * throughout.
 *
 * <p>Nothing addresses the publication at the source, because nothing needs to: its address is
 * derivable from the {@code sourceId} this row already carries.
 */
@MappedEntity("contrato_menor")
public record ContratoMenor(
    @Id @GeneratedValue @Nullable ContratoMenorId id,
    long sourceId,
    UUID organoId,
    @Nullable LocalDate publicationDate,
    @Nullable String obxecto,
    @Nullable Money amount,
    @Nullable String duration,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("operador_economico_id")
        @Nullable OperadorEconomico operadorEconomico) {

  public ContratoMenor {
    Objects.requireNonNull(organoId, "organoId must not be null");
    obxecto = nullIfBlank(obxecto);
    duration = nullIfBlank(duration);
  }

  /** A contract as it is first read from the source: the database assigns its id on insert. */
  public ContratoMenor(
      long sourceId,
      UUID organoId,
      @Nullable LocalDate publicationDate,
      @Nullable String obxecto,
      @Nullable Money amount,
      @Nullable String duration,
      @Nullable OperadorEconomico operadorEconomico) {
    this(
        null,
        sourceId,
        organoId,
        publicationDate,
        obxecto,
        amount,
        duration,
        operadorEconomico);
  }

  private static @Nullable String nullIfBlank(@Nullable String value) {
    return value == null || Whitespace.isBlank(value) ? null : value;
  }
}
