package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored award's identity. Its own type on {@link LicitacionId}'s shape, so the compiler refuses
 * an award's identifier where a formalisation's belongs — the two are separate publications about
 * one award point and are the pair most easily confused.
 *
 * <p><strong>An award keys on an identity of its own rather than on the pair that names its award
 * point.</strong> That pair — the procedure and the lote — carries a lote reference that is null
 * for the ordinary lotless procedure, and a null cannot sit in a primary key; the pair is the
 * unique key beside this one. The identity is also what lets a restated award stay the same award
 * while its amount, its resolution and its awardee change underneath.
 *
 * <p>{@code toString} is the bare UUID because messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = AwardIdConverter.class)
public record AwardId(UUID value) {

  public AwardId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
