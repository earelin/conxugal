package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.convert.ConversionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FiscalIdentifierConverterTest {

  private final FiscalIdentifierConverter converter = new FiscalIdentifierConverter();

  @Test
  void persists_the_canonical_form_and_nothing_beside_it() {
    FiscalIdentifier identifier = new FiscalIdentifier(" b12345678 ");

    assertThat(converter.convertToPersistedValue(identifier, ConversionContext.DEFAULT))
        .isEqualTo("B12345678");
  }

  @Test
  void round_trips_an_identifier_through_the_column() {
    FiscalIdentifier identifier = new FiscalIdentifier("B-1234 5678");

    String persisted = converter.convertToPersistedValue(identifier, ConversionContext.DEFAULT);

    assertThat(converter.convertToEntityValue(persisted, ConversionContext.DEFAULT))
        .isEqualTo(identifier);
  }

  /** A column written before this type existed, or by hand, is reduced rather than trusted. */
  @Test
  void reduces_the_column_holding_an_identifier_that_is_not_canonical() {
    assertThat(converter.convertToEntityValue(" b12345678 ", ConversionContext.DEFAULT))
        .isEqualTo(new FiscalIdentifier("B12345678"));
  }

  @Test
  void persists_no_identifier_as_null() {
    assertThat(converter.convertToPersistedValue(null, ConversionContext.DEFAULT)).isNull();
  }

  @Test
  void reads_the_null_column_back_as_no_identifier() {
    assertThat(converter.convertToEntityValue(null, ConversionContext.DEFAULT)).isNull();
  }

  /**
   * A placeholder is turned away when it is published, not when it is read: a row the store took
   * under one before that rule existed has to come back rather than fail the read that finds it.
   */
  @ParameterizedTest
  @ValueSource(strings = {"-", "TEMP-00934"})
  void reads_back_the_column_holding_placeholder_rather_than_refusing_it(String persisted) {
    assertThat(converter.convertToEntityValue(persisted, ConversionContext.DEFAULT))
        .isEqualTo(new FiscalIdentifier(persisted));
  }

  /**
   * The rebuild the core conversion service is offered, for a column that reaches a caller as bare
   * text. Nothing asks for it today, so what it is pinned against is the attribute half beside it:
   * both rebuild through the canonical constructor, and a column one accepts the other cannot
   * refuse.
   */
  @Test
  void converts_the_bare_column_into_the_canonical_identifier() {
    assertThat(converter.convert(" b12345678 ", FiscalIdentifier.class, ConversionContext.DEFAULT))
        .contains(new FiscalIdentifier("B12345678"));
  }

  @Test
  void converts_the_bare_column_holding_placeholder_rather_than_refusing_it() {
    assertThat(converter.convert("-", FiscalIdentifier.class, ConversionContext.DEFAULT))
        .contains(new FiscalIdentifier("-"));
  }

  @Test
  void converts_an_unusable_column_value_into_no_identifier_rather_than_throwing() {
    assertThat(converter.convert("   ", FiscalIdentifier.class, ConversionContext.DEFAULT))
        .isEmpty();
    assertThat(converter.convert(null, FiscalIdentifier.class, ConversionContext.DEFAULT))
        .isEmpty();
  }
}
