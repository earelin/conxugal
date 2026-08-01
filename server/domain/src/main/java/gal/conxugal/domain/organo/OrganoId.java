package gal.conxugal.domain.organo;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * An Órgano's identity. Its own type so it cannot be passed where a term id is expected — the
 * two sit side by side in the classification use cases. {@code toString} is the bare UUID
 * because exception messages and problem details interpolate the id directly, and it is
 * {@link Serializable} because {@link OrganoNotFoundException} carries one.
 */
@TypeDef(type = DataType.UUID, converter = OrganoIdConverter.class)
public record OrganoId(UUID value) implements Serializable {

  public OrganoId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
