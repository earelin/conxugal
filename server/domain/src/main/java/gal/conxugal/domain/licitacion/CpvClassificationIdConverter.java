package gal.conxugal.domain.licitacion;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link CpvClassificationId} onto its {@code uuid} column. Two interfaces for the reason
 * {@link LoteIdConverter} gives: a database-generated id is read back through the core conversion
 * service rather than through the attribute converter.
 */
@Singleton
public class CpvClassificationIdConverter
    implements AttributeConverter<CpvClassificationId, UUID>,
        TypeConverter<UUID, CpvClassificationId> {

  @Override
  public @Nullable UUID convertToPersistedValue(
      @Nullable CpvClassificationId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable CpvClassificationId convertToEntityValue(
      @Nullable UUID persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new CpvClassificationId(persistedValue);
  }

  @Override
  public Optional<CpvClassificationId> convert(
      UUID object, Class<CpvClassificationId> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(CpvClassificationId::new);
  }
}
