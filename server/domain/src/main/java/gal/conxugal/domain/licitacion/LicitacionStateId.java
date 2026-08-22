package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored licitación state's identity. Its own type so it cannot be passed where another
 * vocabulary's identifier is expected — a procedure names four of them and every one is a UUID
 * underneath, so only the compiler keeps them apart.
 *
 * <p>It is not the value the source published: that is the state's {@code code}, the natural key
 * a re-import matches on. {@code toString} is the bare UUID because messages interpolate the id
 * directly.
 */
@TypeDef(type = DataType.UUID, converter = LicitacionStateIdConverter.class)
public record LicitacionStateId(UUID value) {

  public LicitacionStateId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
