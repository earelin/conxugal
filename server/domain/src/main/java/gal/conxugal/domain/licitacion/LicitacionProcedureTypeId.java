package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored procedure type's identity. Its own type so it cannot be passed where a contract type's
 * or a tramitación type's identifier is expected — the three sit side by side on one procedure,
 * they are all a UUID underneath, and only the compiler keeps them apart.
 *
 * <p>It is not the value the source published: that is the type's {@code name}, the natural key a
 * re-import matches on. {@code toString} is the bare UUID because messages interpolate the id
 * directly.
 */
@TypeDef(type = DataType.UUID, converter = LicitacionProcedureTypeIdConverter.class)
public record LicitacionProcedureTypeId(UUID value) {

  public LicitacionProcedureTypeId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
