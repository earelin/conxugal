package gal.conxugal.domain.licitacion;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link LicitacionContractTypeId} onto its {@code uuid} column. Two interfaces because
 * Micronaut Data reads a database-generated id back through the core conversion service rather
 * than through the attribute converter, so an insert would otherwise fail to set the id it just
 * generated.
 */
@Singleton
public class LicitacionContractTypeIdConverter
    implements AttributeConverter<LicitacionContractTypeId, UUID>,
        TypeConverter<UUID, LicitacionContractTypeId> {

  @Override
  public @Nullable UUID convertToPersistedValue(
      @Nullable LicitacionContractTypeId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable LicitacionContractTypeId convertToEntityValue(
      @Nullable UUID persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new LicitacionContractTypeId(persistedValue);
  }

  @Override
  public Optional<LicitacionContractTypeId> convert(
      UUID object, Class<LicitacionContractTypeId> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(LicitacionContractTypeId::new);
  }
}
