package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.convert.ConversionContext;
import org.junit.jupiter.api.Test;

class PublicationIdConverterTest {

  private final PublicationIdConverter converter = new PublicationIdConverter();

  @Test
  void persists_no_identifier_as_null() {
    assertThat(converter.convertToPersistedValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void reads_the_null_column_back_as_no_identifier() {
    assertThat(converter.convertToEntityValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void persists_the_identifier_as_the_bare_text_the_column_holds() {
    assertThat(converter.convertToPersistedValue(
            new PublicationId("822054"), ConversionContext.DEFAULT))
        .isEqualTo("822054");
  }

  @Test
  void reads_the_stored_text_back_as_the_identifier() {
    assertThat(converter.convertToEntityValue("822054", ConversionContext.DEFAULT))
        .isEqualTo(new PublicationId("822054"));
  }

  @Test
  void round_trips_an_identifier_the_source_did_not_mint_as_number() {
    PublicationId alphanumeric = new PublicationId("LIC-2026/0042");

    assertThat(converter.convertToEntityValue(
            converter.convertToPersistedValue(alphanumeric, ConversionContext.DEFAULT),
            ConversionContext.DEFAULT))
        .isEqualTo(alphanumeric);
  }
}
