package gal.conxugal.domain.licitacion;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Maps {@link PublicationId} onto its {@code text} column, unchanged in either direction — the
 * value is already what the source published, and rebuilding on the way in re-applies the rule
 * rather than trusting a column written by hand.
 *
 * <p><strong>One interface, unlike an aggregate identifier's converter.</strong> That second half
 * exists because a database-generated id is read back through the core conversion service rather
 * than through the attribute converter; this value is never generated — the source supplies it —
 * so the attribute half covers every path it travels as a property of the licitación row.
 *
 * <p>A projection is not one of those paths, on {@code MoneyConverter}'s reasoning: a browse read
 * selecting this column onto a row that is not the aggregate has no mapped property behind it, so
 * whatever adds one will need the conversion-service half this deliberately does not carry yet.
 * This feature exposes no such read.
 */
@Singleton
public class PublicationIdConverter implements AttributeConverter<PublicationId, String> {

  @Override
  public @Nullable String convertToPersistedValue(
      @Nullable PublicationId entityValue, ConversionContext context) {
    return entityValue == null ? null : entityValue.value();
  }

  @Override
  public @Nullable PublicationId convertToEntityValue(
      @Nullable String persistedValue, ConversionContext context) {
    return persistedValue == null ? null : new PublicationId(persistedValue);
  }
}
