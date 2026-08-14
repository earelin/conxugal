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

  /**
   * The path the year facets travel: a column of years with no aggregate behind it, rebuilt
   * through the core conversion service rather than as a property of a mapped row.
   */
  @Test
  void converts_the_column_value_the_facet_read_answers_with() {
    assertThat(converter.convert(2025, YearSelection.class, ConversionContext.DEFAULT))
        .contains(YearSelection.of(2025));
  }

  @Test
  void converts_an_absent_column_value_into_no_selection() {
    assertThat(converter.convert(null, YearSelection.class, ConversionContext.DEFAULT))
        .isEmpty();
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
