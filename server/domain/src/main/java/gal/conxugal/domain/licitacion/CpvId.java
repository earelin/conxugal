package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored CPV entry's identity. Its own type so a classification row cannot be handed a
 * {@link NutId} where this belongs — the two vocabularies are structurally identical and are
 * reached from adjacent components, which is where a mix-up would otherwise be silent.
 *
 * <p>It is not the CPV code: that is {@link Cpv#code()}, the identifier the regulated list assigns
 * and the natural key an import matches on. This is the identity the system assigns, and it is
 * what a classification row's foreign key holds. {@code toString} is the bare UUID because
 * messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = CpvIdConverter.class)
public record CpvId(UUID value) {

  public CpvId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
