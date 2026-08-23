package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One licitación as the Órgano's listing published it. This is everything the cheap retrieval
 * knows, and it is not a whole procedure: the expediente, the types, the lotes, the bidders and
 * the awards are all on the per-procedure record and nowhere else.
 *
 * <p><strong>It is nevertheless indispensable rather than merely a pointer.</strong> Four of
 * {@link Licitacion}'s values — both dates and both halves of the state — are published here and
 * not on the record, which publishes the state's label alone and so cannot distinguish the two
 * codes that share one. Whatever stores a procedure needs this entry beside the parsed record.
 *
 * <p><strong>{@code baseBudget} is the budget, not an award.</strong> The listing's published
 * amount is the procedure's <em>orzamento base de licitación</em>; what its lotes were awarded
 * appears nowhere in the listing. The field is named for what it is because taking it for an
 * awarded amount would fill every cross-family total and every operador history with budgets,
 * silently and plausibly.
 *
 * <p>Only the publication identifier and the state code are required. Every other value is
 * nullable, and null means the source published nothing there — including a date whose text could
 * not be interpreted, which is not a reason to refuse the entry.
 *
 * <p>Text arrives as the adapter stripped it, and a value carrying nothing is held as null rather
 * than as an empty string, the way {@link LicitacionOutstandingRecord} holds the same values.
 */
public record LicitacionListingEntry(
    PublicationId publicationId,
    @Nullable LocalDate publicationDate,
    @Nullable LocalDate lastModified,
    @Nullable String obxecto,
    @Nullable Money baseBudget,
    int stateCode,
    @Nullable String stateLabel) {

  public LicitacionListingEntry {
    Objects.requireNonNull(publicationId, "publicationId must not be null");
    obxecto = PublishedText.orNullWhenBlank(obxecto);
    stateLabel = PublishedText.orNullWhenBlank(stateLabel);
  }
}
