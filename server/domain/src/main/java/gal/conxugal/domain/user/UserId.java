package gal.conxugal.domain.user;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * An account's identity. Its own type so it cannot be passed where another entity's id is
 * expected. {@code toString} is the bare UUID because exception messages and problem details
 * interpolate the id directly, and it is {@link Serializable} because
 * {@link UserNotFoundException} and {@link LastEnabledAdminException} carry one.
 */
@TypeDef(type = DataType.UUID, converter = UserIdConverter.class)
public record UserId(UUID value) implements Serializable {

  public UserId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
