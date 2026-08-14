package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.convert.ConversionContext;
import org.junit.jupiter.api.Test;

class YearSelectionConverterTest {

  private final YearSelectionConverter converter = new YearSelectionConverter();

  @Test
  void persists_an_absent_selection_as_null() {
    assertThat(converter.convertToPersistedValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void reads_the_null_column_back_as_an_absent_selection() {
    assertThat(converter.convertToEntityValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void persists_the_selection_as_the_bare_year() {
    assertThat(converter.convertToPersistedValue(YearSelection.of(2025), ConversionContext.DEFAULT))
        .isEqualTo(2025);
  }

  @Test
  void round_trips_the_year_through_the_column() {
    YearSelection selection = YearSelection.of(2019);

    YearSelection restored =
        converter.convertToEntityValue(
            converter.convertToPersistedValue(selection, ConversionContext.DEFAULT),
            ConversionContext.DEFAULT);

    assertThat(restored)
        .isEqualTo(selection);
  }
}
