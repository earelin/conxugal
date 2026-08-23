package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A NUTS entry the source published for a procedure, with the date it was diffused on.
 * Structurally identical to {@link CpvClassification} and separate from it because the two map two
 * tables and one mapped entity cannot map both — see that record for the reasoning in full,
 * including why the lote reference is nullable, what a null one means, and why the entry itself is
 * a reference to {@link Nut} rather than a code held here.
 *
 * <p>The measurement is this one's own: over 240 procedures the NUT table wrote the procedure-wide
 * marker {@code _} on 217 rows and a lote on the rest, so the procedure-wide row is the ordinary
 * case here rather than the exception.
 */
@MappedEntity("licitacion_nut")
public record NutClassification(
    @Id @GeneratedValue @Nullable NutClassificationId id,
    LicitacionId licitacionId,
    @Nullable LoteId loteId,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("nut_id") Nut nut,
    @Nullable LocalDate diffusionDate,
    boolean withdrawn) {

  public NutClassification {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(nut, "nut must not be null");
  }

  /**
   * A classification as it is first read from the source: the database assigns its id on insert,
   * and nothing an import does withdraws it.
   */
  public NutClassification(
      LicitacionId licitacionId,
      @Nullable LoteId loteId,
      Nut nut,
      @Nullable LocalDate diffusionDate) {
    this(null, licitacionId, loteId, nut, diffusionDate, false);
  }

  /**
   * Identity, not contents, on {@link CpvClassification#equals}' reasoning: two instances are the
   * same classification when they carry the same assigned {@link NutClassificationId}, and one the
   * database has not assigned an identity to is equal only to itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof NutClassification classification
        && id != null
        && id.equals(classification.id);
  }

  @Override
  public int hashCode() {
    return id == null ? System.identityHashCode(this) : id.hashCode();
  }
}
