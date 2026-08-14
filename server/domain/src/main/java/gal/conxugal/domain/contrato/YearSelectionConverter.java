package gal.conxugal.domain.contrato;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link YearSelection} onto the {@code integer} column it is compared against, unchanged in
 * either direction, so a read can take a selection as a query parameter rather than unwrapping one
 * at the boundary.
 *
 * <p>Two interfaces, because a year travels in both directions and only one of them is an
 * attribute of a mapped row. The attribute half converts the selection a read is scoped by. The
 * {@code TypeConverter} half covers the other direction: the years an Órgano has contracts in are
 * read back as a column of their own, with no aggregate behind them to carry a mapping, so the
 * core conversion service is what rebuilds each one.
 *
 * <p>It sits beside the type rather than beside the adapter because the type definition on
 * {@link YearSelection} names this class, and that mapping is resolved while this module compiles.
 */
@Singleton
public class YearSelectionConverter
    implements AttributeConverter<YearSelection, Integer>, TypeConverter<Integer, YearSelection> {

  @Override
  public @Nullable Integer convertToPersistedValue(
      @Nullable YearSelection entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.year();
  }

  @Override
  public @Nullable YearSelection convertToEntityValue(
      @Nullable Integer persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new YearSelection(persistedValue);
  }

  @Override
  public Optional<YearSelection> convert(
      Integer object, Class<YearSelection> targetType, ConversionContext context) {
    return Optional.ofNullable(object).map(YearSelection::new);
  }
}
