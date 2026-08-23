package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.convert.ConversionContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AwardIdConverterTest {

  private final AwardIdConverter converter = new AwardIdConverter();

  @Test
  void persists_an_unassigned_identity_as_null() {
    assertThat(converter.convertToPersistedValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void reads_the_null_column_back_as_an_unassigned_identity() {
    assertThat(converter.convertToEntityValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void persists_the_identity_as_the_bare_uuid_the_column_holds() {
    UUID value = UUID.randomUUID();
    AwardId id = new AwardId(value);

    assertThat(converter.convertToPersistedValue(id, ConversionContext.DEFAULT))
        .isEqualTo(value);
  }

  @Test
  void reads_the_stored_uuid_back_as_the_identity() {
    UUID value = UUID.randomUUID();

    assertThat(converter.convertToEntityValue(value, ConversionContext.DEFAULT))
        .isEqualTo(new AwardId(value));
  }

  @Test
  void converts_the_generated_key_through_the_conversion_service_half() {
    UUID generated = UUID.randomUUID();

    // The half an attribute converter alone does not cover: a database-assigned id is read back
    // through the core conversion service, so without this an insert would return a null id.
    assertThat(converter.convert(generated, AwardId.class, ConversionContext.DEFAULT))
        .contains(new AwardId(generated));
  }

  @Test
  void converts_no_generated_key_into_an_empty_result() {
    assertThat(converter.convert(null, AwardId.class, ConversionContext.DEFAULT))
        .isEmpty();
  }
}
