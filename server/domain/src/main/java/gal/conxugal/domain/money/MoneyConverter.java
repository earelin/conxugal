package gal.conxugal.domain.money;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link Money} onto its {@code numeric} column, unchanged in either direction — the column
 * decides the scale it stores, and nothing here imposes one.
 *
 * <p>Unlike an identifier's converter this needs no {@code TypeConverter} half: an amount is
 * never a generated key read back through the core conversion service, so this covers every path
 * an entity property travels. A projection is not one of them — a query summing amounts has no
 * mapped property behind its result, so whatever adds one will need the conversion-service half
 * that this deliberately does not carry yet.
 */
@Singleton
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

  @Override
  public @Nullable BigDecimal convertToPersistedValue(
      @Nullable Money entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable Money convertToEntityValue(
      @Nullable BigDecimal persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new Money(persistedValue);
  }
}
