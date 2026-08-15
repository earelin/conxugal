package gal.conxugal.domain.contrato;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One row of a browse read: everything a reader is shown about a contrato menor, and nothing else.
 *
 * <p><b>Four of its values cannot be absent</b> — the publication date, the amount, the awardee's
 * name and the awardee's fiscal identifier — and that is the withholding rule expressed as a type
 * rather than as a filter every reader has to trust. A contract missing any of them is stored but
 * is not a visible contract, so it reaches no page and no count, and nothing downstream needs a
 * branch for a row that has none. Only the object and the duration can be absent, because the
 * source genuinely publishes neither for some awards.
 *
 * <p><b>It is a projection, not the {@link ContratoMenor} aggregate</b>, and deliberately so. The
 * aggregate's awardee is an {@link gal.conxugal.domain.operador.OperadorEconomico} carrying an
 * embedded rank and a set of alternative names; assembling one per row — aliasing an embedded
 * value and collecting a relation — would be the cost of reaching the two fields a row actually
 * shows. This is the smaller thing that answers the question, and it holds no identity of its own
 * because a contrato menor has no detail view to address.
 *
 * <p><b>Nothing maps a result row onto it automatically</b>, and that is not for want of trying:
 * Micronaut Data builds a projection's mapping outside the container, so a component whose type
 * names an attribute converter — both {@link Money} and
 * {@link gal.conxugal.domain.operador.FiscalIdentifier} do — fails to read with <em>Converters not
 * supported</em>. The adapter assembles each row by hand instead. The alternative was to hold the
 * two as {@code BigDecimal} and {@code String} and move the conversions to every reader, which is
 * the guarantee this record exists to give rather than a detail of how it is filled.
 *
 * <p>It stays introspected for the driving side, which serialises it.
 */
@Introspected
public record VisibleContratoMenor(
    long sourceId,
    LocalDate publicationDate,
    @Nullable String obxecto,
    Money amount,
    @Nullable String duration,
    String awardeeName,
    FiscalIdentifier awardeeFiscalId) {

  public VisibleContratoMenor {
    Objects.requireNonNull(publicationDate, "publicationDate must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(awardeeName, "awardeeName must not be null");
    Objects.requireNonNull(awardeeFiscalId, "awardeeFiscalId must not be null");
  }
}
