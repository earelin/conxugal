package gal.conxugal.domain.contrato;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored contrato menor's identity. Its own type so it cannot be passed where an Órgano's or an
 * operador's identifier is expected — the import threads all of them through one walk, and a
 * mix-up is a compile error here rather than a missing row later.
 *
 * <p>It is not the source's identifier: that is the contract's {@code sourceId}, the natural key
 * a re-import matches on. This is the identity the system assigns. {@code toString} is the bare
 * UUID because messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = ContratoMenorIdConverter.class)
public record ContratoMenorId(UUID value) {

  public ContratoMenorId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
