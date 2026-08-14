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
 * <p>It is introspected so a result row can be read onto it directly. Introspection settles the
 * shape and not the conversion: a component inherits a converted type's mapping only where the
 * aggregate this projects from carries a property of the same name and type. The amount does —
 * the contract holds one — while the awardee's identifier does not, because the contract reaches
 * its awardee through a relation rather than holding the value. That one is converted through the
 * core conversion service instead, which is why its converter carries a second half.
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
