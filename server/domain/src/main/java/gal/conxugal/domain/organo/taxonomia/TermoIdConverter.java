package gal.conxugal.domain.organo.taxonomia;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link TermoId} onto its {@code uuid} column. Two interfaces because Micronaut Data reads
 * a database-generated id back through the core conversion service rather than through the
 * attribute converter, so an insert would otherwise fail to set the id it just generated.
 */
@Singleton
public class TermoIdConverter
    implements AttributeConverter<TermoId, UUID>, TypeConverter<UUID, TermoId> {

  @Override
  public @Nullable UUID convertToPersistedValue(
      @Nullable TermoId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable TermoId convertToEntityValue(
      @Nullable UUID persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new TermoId(persistedValue);
  }

  @Override
  public Optional<TermoId> convert(
      UUID object, Class<TermoId> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(TermoId::new);
  }
}
