package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * An operador económico's identity. Its own type so it cannot be passed where an Órgano's or a
 * contract's identifier is expected — the import threads all of them through one walk.
 * {@code toString} is the bare UUID because messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = OperadorIdConverter.class)
public record OperadorId(UUID value) {

  public OperadorId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
