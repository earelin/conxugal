package gal.conxugal.domain.organo.taxonomia;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * A term's identity. Its own type so a term id cannot be passed where an Órgano id is expected —
 * the two are both UUIDs and sit side by side in several signatures. {@code toString} is the bare
 * UUID because exception messages and problem details interpolate the id directly, and it is
 * {@link Serializable} because the taxonomy exceptions carry one and are themselves serializable.
 */
@TypeDef(type = DataType.UUID, converter = TermoIdConverter.class)
public record TermoId(UUID value) implements Serializable {

  public TermoId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
