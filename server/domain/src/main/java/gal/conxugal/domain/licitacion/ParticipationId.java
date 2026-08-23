package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored participation's identity. Its own type so it cannot be passed where a lote's, the
 * procedure's or an operador's identifier is expected — a participation carries all three beside
 * this one.
 *
 * <p>Nothing else is filed under it. UTE membership relates two operadores and lives in
 * {@code gal.conxugal.domain.operador}, so a bid is referred to by nothing and this identity
 * serves the row alone. {@code toString} is the bare UUID because messages interpolate the id
 * directly.
 */
@TypeDef(type = DataType.UUID, converter = ParticipationIdConverter.class)
public record ParticipationId(UUID value) {

  public ParticipationId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
