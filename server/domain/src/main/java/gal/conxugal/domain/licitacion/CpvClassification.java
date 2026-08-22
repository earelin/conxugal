package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A CPV code the source published for a procedure, with the date it was diffused on. {@code id} is
 * a system-assigned identity, {@code null} only until the database assigns it.
 *
 * <p><strong>The lote is nullable, and that is the departure worth stating.</strong> A null one
 * means <em>the procedure as a whole</em> rather than <em>unattached</em>, and it is what the
 * source publishes: on procedure 822054 — two lotes, two separate awards — every CPV row's lote
 * cell read {@code _}, the procedure-wide marker. A model requiring a lote on every classification
 * row of a procedure that has lotes could not store what the source publishes. The no-second-copy
 * rule is untouched by this: where a classification is procedure-wide, the procedure-level row is
 * the <em>only</em> row rather than a duplicate of per-lote ones.
 *
 * <p>The procedure reference is required on every row, including the procedure-wide one — a
 * classification always belongs to a procedure, and a lotless procedure would otherwise have
 * nowhere to hang it. The diffusion date is nullable because a date that could not be interpreted
 * is absent rather than a reason to refuse the row.
 *
 * <p><strong>Its own record rather than one shared with {@link NutClassification}</strong>, because
 * the two map two tables and one mapped entity cannot map both. Nothing else distinguishes them,
 * and nothing needs to.
 *
 * <p>{@code withdrawn} is the marker R13's retention needs. Nothing here writes it — the
 * reconciliation that does is a later task's.
 */
@MappedEntity("licitacion_cpv")
public record CpvClassification(
    @Id @GeneratedValue @Nullable CpvClassificationId id,
    LicitacionId licitacionId,
    @Nullable LoteId loteId,
    String code,
    @Nullable LocalDate diffusionDate,
    boolean withdrawn) {

  public CpvClassification {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    code = PublishedKey.canonical(code, "code");
  }

  /**
   * A classification as it is first read from the source: the database assigns its id on insert,
   * and nothing an import does withdraws it.
   */
  public CpvClassification(
      LicitacionId licitacionId,
      @Nullable LoteId loteId,
      String code,
      @Nullable LocalDate diffusionDate) {
    this(null, licitacionId, loteId, code, diffusionDate, false);
  }

  /**
   * Identity, not contents: two instances are the same classification when they carry the same
   * assigned {@link CpvClassificationId}. The record's own equality would compare the diffusion
   * date and the withdrawal marker too, so the same stored row read either side of a restatement
   * would compare unequal to itself.
   *
   * <p>A classification the database has not assigned an identity to is equal only to itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof CpvClassification classification
        && id != null
        && id.equals(classification.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
