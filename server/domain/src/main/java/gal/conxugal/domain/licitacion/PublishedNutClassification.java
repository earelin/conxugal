package gal.conxugal.domain.licitacion;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One row of the record's NUT table, as the source published it and before it becomes a
 * {@link NutClassification}. Structurally identical to {@link PublishedCpvClassification} — see
 * that record for the reasoning in full, which holds here unchanged: the code names an entry
 * rather than being a value on the row, the description rides in the same cell behind a
 * non-breaking space ({@code ES111}, then {@code A Coruña}), and a null lote means the procedure
 * as a whole.
 *
 * <p><strong>A separate record rather than one shared with the CPV row</strong>, for the reason
 * {@link Nut} gives for the entities: a CPV names what is bought and a NUTS names where, so a row
 * filed under the wrong vocabulary is not a visible error but a procedure classified by a region
 * as though it were a purpose. Two types are what make that a compile error rather than a silent
 * one at the point the parse hands the two lists over.
 *
 * <p>The measurement is this one's own: over 240 procedures the NUT table wrote the
 * procedure-wide marker {@code _} on 217 rows and a lote on the rest, so the procedure-wide row is
 * the ordinary case here rather than the exception.
 *
 * @param code the regulated list's own identifier for the entry, which is what an import matches
 *     on
 * @param description the wording the cell carries beside the code, where it carries any
 * @param loteKey the award point this row classifies, reduced by {@link LoteKey}; null is the
 *     procedure as a whole
 * @param diffusionDate the published {@code Data difusión}
 */
public record PublishedNutClassification(
    String code,
    @Nullable String description,
    @Nullable String loteKey,
    @Nullable LocalDate diffusionDate) {

  /** The code is required, on {@link PublishedCpvClassification}'s reasoning. */
  public PublishedNutClassification {
    code = PublishedKey.canonical(code, "code");
    description = PublishedText.orNullWhenBlank(description);
    loteKey = LoteKey.normalise(loteKey).orElse(null);
  }
}
