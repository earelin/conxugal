package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored formalisation's identity. Its own type on {@link LicitacionId}'s shape, so the compiler
 * refuses a formalisation's identifier where an award's belongs — the two sit on the same award
 * point and are allowed to disagree about who the party is.
 *
 * <p>It keys on an identity of its own for {@link AwardId}'s reason: the pair that names its award
 * point carries a lote reference that is null on a lotless procedure, and a null cannot sit in a
 * primary key.
 *
 * <p>{@code toString} is the bare UUID because messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = FormalisationIdConverter.class)
public record FormalisationId(UUID value) {

  public FormalisationId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
