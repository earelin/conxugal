package gal.conxugal.domain.importrun;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Objects;
import java.util.UUID;

/**
 * A run's identity. Its own type because this identifier passes through more hands than any other
 * here — a claim returns it, a trigger answers with it, the run read is keyed by it — and every
 * one of those hands also holds an Órgano's identifier.
 *
 * <p>{@code toString} is the bare UUID because messages interpolate the id directly.
 */
@TypeDef(type = DataType.UUID, converter = ImportRunIdConverter.class)
public record ImportRunId(UUID value) {

  public ImportRunId {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
