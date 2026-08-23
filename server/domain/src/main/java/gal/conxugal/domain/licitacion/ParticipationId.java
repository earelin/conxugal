package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored participation's identity. Its own type so it cannot be passed where a lote's or the
 * procedure's identifier is expected — a participation carries all three, and a
 * {@link UteMembership} is filed under this one alone.
 *
 * <p>It is what makes a consortium's membership expressible whether or not the source identified
 * the consortium: the membership hangs off the bid that was made, never off a UTE operador that
 * may not exist. {@code toString} is the bare UUID because messages interpolate the id directly.
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
