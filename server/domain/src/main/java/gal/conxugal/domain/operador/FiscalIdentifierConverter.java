package gal.conxugal.domain.operador;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link FiscalIdentifier} onto its {@code text} column. Nothing reduces on the way out — the
 * value is already canonical — and rebuilding on the way in reduces a column written before this
 * type existed, or by hand, rather than trusting it.
 *
 * <p>Two interfaces, for the two paths this value now travels. The attribute half covers it as a
 * property of the operador row. The {@code TypeConverter} half covers it as a column selected onto
 * a projection: a projection's component inherits a converter only where the aggregate it is
 * projected from carries a property of the same name and type, and the identifier a contract's row
 * shows is joined in from the operador rather than held on the contract — so it arrives as bare
 * text and is converted through the core conversion service instead.
 *
 * <p>That half answers nothing at all for a value it cannot canonicalise, rather than throwing
 * from inside a conversion: {@link FiscalIdentifier#of} already decides which published values are
 * usable, and this defers to it rather than deciding again.
 */
@Singleton
public class FiscalIdentifierConverter
    implements AttributeConverter<FiscalIdentifier, String>,
        TypeConverter<String, FiscalIdentifier> {

  @Override
  public @Nullable String convertToPersistedValue(
      @Nullable FiscalIdentifier entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable FiscalIdentifier convertToEntityValue(
      @Nullable String persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new FiscalIdentifier(persistedValue);
  }

  @Override
  public Optional<FiscalIdentifier> convert(
      String object, Class<FiscalIdentifier> targetType, ConversionContext context) {
    return FiscalIdentifier.of(object);
  }
}
