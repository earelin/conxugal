package gal.conxugal.domain.licitacion;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored lote's identity. Its own type so it cannot be passed where the procedure's identifier
 * is expected — a classification, an award, a formalisation and a participation each carry both
 * side by side, and a mix-up is a compile error here rather than a row filed under the wrong
 * thing.
 *
 * <p>It is not the lote's published identifier: that is {@link Lote#identifier()}, the text the
 * source prints in a lote column. This is the identity the system assigns, and it is what the four
 * rows that reference a lote hold. {@code toString} is the bare UUID because messages interpolate
 * the id directly.
 */
@TypeDef(type = DataType.UUID, converter = LoteIdConverter.class)
public record LoteId(UUID value) {

  public LoteId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
