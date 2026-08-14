package gal.conxugal.domain.contrato;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link YearSelection} onto the {@code integer} column it is compared against, unchanged in
 * either direction, so a read can take a selection as a query parameter rather than unwrapping one
 * at the boundary.
 *
 * <p>Unlike an identifier's converter this needs no {@code TypeConverter} half: that half exists
 * because Micronaut Data reads a database-generated key back through the core conversion service,
 * and a year is never generated — it arrives with the question and is never read back.
 */
@Singleton
public class YearSelectionConverter implements AttributeConverter<YearSelection, Integer> {

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
}
