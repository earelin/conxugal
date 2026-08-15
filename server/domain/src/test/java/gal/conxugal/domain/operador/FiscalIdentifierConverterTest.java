package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.convert.ConversionContext;
import org.junit.jupiter.api.Test;

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
   * The path a browse row travels: the identifier is joined in from the operador rather than held
   * on the contract, so a projection reads it as bare text and rebuilds it through the core
   * conversion service instead of through the attribute half above.
   */
  @Test
  void converts_the_joined_column_into_the_canonical_identifier() {
    assertThat(converter.convert(" b12345678 ", FiscalIdentifier.class, ConversionContext.DEFAULT))
        .contains(new FiscalIdentifier("B12345678"));
  }

  @Test
  void converts_an_unusable_column_value_into_no_identifier_rather_than_throwing() {
    assertThat(converter.convert("   ", FiscalIdentifier.class, ConversionContext.DEFAULT))
        .isEmpty();
    assertThat(converter.convert(null, FiscalIdentifier.class, ConversionContext.DEFAULT))
        .isEmpty();
  }
}
