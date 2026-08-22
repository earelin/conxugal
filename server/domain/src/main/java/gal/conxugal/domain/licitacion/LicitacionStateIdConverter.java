package gal.conxugal.domain.licitacion;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link LicitacionStateId} onto its {@code uuid} column. Two interfaces because Micronaut
 * Data reads a database-generated id back through the core conversion service rather than through
 * the attribute converter, so an insert would otherwise fail to set the id it just generated.
 */
@Singleton
public class LicitacionStateIdConverter
    implements AttributeConverter<LicitacionStateId, UUID>,
        TypeConverter<UUID, LicitacionStateId> {

  @Override
  public @Nullable UUID convertToPersistedValue(
      @Nullable LicitacionStateId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable LicitacionStateId convertToEntityValue(
      @Nullable UUID persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new LicitacionStateId(persistedValue);
  }

  @Override
  public Optional<LicitacionStateId> convert(
      UUID object, Class<LicitacionStateId> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(LicitacionStateId::new);
  }
}
