package gal.conxugal.domain.operador;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link FiscalIdentifier} onto its {@code text} column. Nothing reduces on the way out — the
 * value is already canonical — and rebuilding on the way in reduces a column written before this
 * type existed, or by hand, rather than trusting it.
 *
 * <p>Like an amount's converter and unlike an identity's, this needs no {@code TypeConverter}
 * half: the fiscal identifier is a published value on the row, never a generated key read back
 * through the core conversion service.
 */
@Singleton
public class FiscalIdentifierConverter implements AttributeConverter<FiscalIdentifier, String> {

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
}
