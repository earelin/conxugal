package gal.conxugal.domain.licitacion;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link AwardId} onto its {@code uuid} column. Two interfaces for the reason
 * {@link LoteIdConverter} gives: a database-generated id is read back through the core conversion
 * service rather than through the attribute converter.
 */
@Singleton
public class AwardIdConverter
    implements AttributeConverter<AwardId, UUID>, TypeConverter<UUID, AwardId> {

  @Override
  public @Nullable UUID convertToPersistedValue(
      @Nullable AwardId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable AwardId convertToEntityValue(
      @Nullable UUID persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new AwardId(persistedValue);
  }

  @Override
  public Optional<AwardId> convert(
      UUID object, Class<AwardId> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(AwardId::new);
  }
}
