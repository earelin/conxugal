package gal.conxugal.domain.importrun;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link ImportRunId} onto its {@code uuid} column. Two interfaces because Micronaut Data
 * reads a database-generated id back through the core conversion service rather than through the
 * attribute converter, so an insert would otherwise fail to set the id it just generated — which
 * is precisely the id a claim has to return.
 *
 * <p>It sits beside the id rather than beside the adapter because the type definition on
 * {@link ImportRunId} names this class, and that mapping is resolved while this module compiles —
 * a converter living with the adapter would be found too late.
 */
@Singleton
public class ImportRunIdConverter
    implements AttributeConverter<ImportRunId, UUID>, TypeConverter<UUID, ImportRunId> {

  @Override
  public @Nullable UUID convertToPersistedValue(
      @Nullable ImportRunId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable ImportRunId convertToEntityValue(
      @Nullable UUID persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new ImportRunId(persistedValue);
  }

  @Override
  public Optional<ImportRunId> convert(
      UUID object, Class<ImportRunId> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(ImportRunId::new);
  }
}
