package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored licitación's identity. Its own type so it cannot be passed where an Órgano's, a state's
 * or a type's identifier is expected — the import threads all of them through one walk, and a
 * mix-up is a compile error here rather than a missing row later.
 *
 * <p>It is not the source's identifier: that is the procedure's {@code publicationId}, the natural
 * key a re-import matches on. This is the identity the system assigns. {@code toString} is the
 * bare UUID because messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = LicitacionIdConverter.class)
public record LicitacionId(UUID value) {

  public LicitacionId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
