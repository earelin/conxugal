package gal.conxugal.domain.operador;

import gal.conxugal.commons.text.Whitespace;
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
 * <p>Two interfaces. The attribute half covers the identifier as a property of the operador row,
 * and is the one every read of that row goes through. The {@code TypeConverter} half offers the
 * same rebuild to the core conversion service, for a column that reaches a caller as bare text:
 * a projection's component inherits a converter only where the aggregate it is projected from
 * carries a property of the same name and type, and the identifier a contract's row shows is
 * joined in from the operador rather than held on the contract.
 *
 * <p><b>Nothing reaches that half today.</b> The browse projection Micronaut Data could not map
 * is assembled by hand in its adapter instead, and no other caller asks the conversion service for
 * this type. It is kept because the conversion is the same one and registering it costs nothing —
 * but a reader should not take its presence as evidence of a path.
 *
 * <p><b>Both halves rebuild through the canonical constructor, and neither through
 * {@link FiscalIdentifier#of}.</b> Everything arriving here has already been stored, so the
 * question is what the column holds and not whether a source should have published it — and
 * {@code of} turns away published placeholders, which is the opposite of what a value already in
 * the column needs. Routing a read through it would make a row the store accepted unreadable.
 *
 * <p>The {@code TypeConverter} half still answers nothing at all for a column it cannot
 * canonicalise, rather than throwing from inside a conversion; only an empty column can reach
 * that branch, since no other value fails to canonicalise.
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
      @Nullable String object, Class<FiscalIdentifier> targetType, ConversionContext context) {
    if (object == null || Whitespace.isBlank(object)) {
      return Optional.empty();
    }
    return Optional.of(new FiscalIdentifier(object));
  }
}
