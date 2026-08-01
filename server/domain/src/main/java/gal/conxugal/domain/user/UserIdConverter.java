package gal.conxugal.domain.user;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link UserId} onto its {@code uuid} column. Two interfaces because Micronaut Data reads
 * a database-generated id back through the core conversion service rather than through the
 * attribute converter, so an insert would otherwise fail to set the id it just generated.
 *
 * <p>It sits beside the id rather than beside the adapter because the type definition on
 * {@link UserId} names this class, and that mapping is resolved while this module compiles —
 * a converter living with the adapter would be found too late.
 */
@Singleton
public class UserIdConverter
    implements AttributeConverter<UserId, UUID>, TypeConverter<UUID, UserId> {

  @Override
  public @Nullable UUID convertToPersistedValue(
      @Nullable UserId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable UserId convertToEntityValue(
      @Nullable UUID persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new UserId(persistedValue);
  }

  @Override
  public Optional<UserId> convert(
      UUID object, Class<UserId> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(UserId::new);
  }
}
