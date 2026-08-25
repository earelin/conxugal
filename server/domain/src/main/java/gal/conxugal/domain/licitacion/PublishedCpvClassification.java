package gal.conxugal.domain.licitacion;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One row of the record's CPV table, as the source published it and before it becomes a
 * {@link CpvClassification}.
 *
 * <p><strong>It carries a code rather than an entry.</strong> {@link Cpv} is the regulated list's
 * row, not this procedure's, so whatever stores this upserts the entry first — matched on the
 * code, never on a description — and hands the classification the entry it got back. The row's
 * foreign key needs an identity that exists, which is the same ordering the state and the three
 * types on the procedure itself are stored in.
 *
 * <p><strong>The description is published beside the code, in the same cell.</strong> On 822054
 * the cell reads {@code 45000000}, a non-breaking space, then {@code Trabajos de construcción}.
 * It is carried because {@link Cpv} has somewhere to put it and discarding a value the source
 * publishes costs a re-read to recover. Nothing matches on it: the wording is translated, revised
 * and repeated across sibling entries, so an import unique on it would reject a real entry.
 *
 * <p><strong>A null lote means the procedure as a whole, even on a procedure that has
 * lotes.</strong> That is not a degenerate case: on 822054 — two lotes, two separate awards —
 * every CPV row's lote cell reads {@code _}. A model requiring a lote on every classification row
 * of a procedure that has lotes could not store what the source publishes.
 *
 * @param code the regulated list's own identifier for the entry, which is what an import matches
 *     on
 * @param description the wording the cell carries beside the code, where it carries any
 * @param loteKey the award point this row classifies, reduced by {@link LoteKey}; null is the
 *     procedure as a whole
 * @param diffusionDate the published {@code Data difusión}
 */
public record PublishedCpvClassification(
    String code,
    @Nullable String description,
    @Nullable String loteKey,
    @Nullable LocalDate diffusionDate) {

  /**
   * The code is required: a classification keyed on nothing names nothing, and {@link Cpv} is
   * unique on it. A row whose code cell is empty is dropped by the parse rather than refused
   * here — there is no row to make, and it is not a structural failure of the table.
   */
  public PublishedCpvClassification {
    code = PublishedKey.canonical(code, "code");
    description = PublishedText.orNullWhenBlank(description);
    loteKey = LoteKey.normalise(loteKey).orElse(null);
  }
}
