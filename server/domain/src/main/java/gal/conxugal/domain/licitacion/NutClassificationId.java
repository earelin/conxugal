package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored NUT classification's identity. Its own type rather than one shared with
 * {@link CpvClassificationId}, because the two classifications are two tables and nothing may file
 * a row of one under an identity minted for the other.
 *
 * <p>It keys on an identity of its own for {@link AwardId}'s reason: the triple that names a
 * classification row carries a lote reference that is null wherever the source published the
 * classification against the procedure as a whole, and a null cannot sit in a primary key.
 *
 * <p>{@code toString} is the bare UUID because messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = NutClassificationIdConverter.class)
public record NutClassificationId(UUID value) {

  public NutClassificationId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
