package gal.conxugal.domain.money;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.convert.ConversionContext;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MoneyConverterTest {

  private final MoneyConverter converter = new MoneyConverter();

  @Test
  void persists_an_absent_amount_as_null() {
    assertThat(converter.convertToPersistedValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void reads_the_null_column_back_as_an_absent_amount() {
    assertThat(converter.convertToEntityValue(null, ConversionContext.DEFAULT))
        .isNull();
  }

  @Test
  void persists_the_published_figure_without_rescaling_it() {
    Money amount = new Money(new BigDecimal("3630.00"));

    BigDecimal persisted = converter.convertToPersistedValue(amount, ConversionContext.DEFAULT);

    assertThat(persisted)
        .isNotNull()
        .hasToString("3630.00");
  }

  @Test
  void round_trips_the_figure_carrying_more_decimals_than_two() {
    Money amount = new Money(new BigDecimal("1234.5678"));

    Money restored = roundTrip(amount);

    assertThat(restored)
        .isEqualTo(amount);
    assertThat(restored)
        .isNotNull();
    assertThat(restored.value().scale())
        .isEqualTo(4);
  }

  @Test
  void round_trips_the_figure_carrying_no_decimals() {
    Money amount = new Money(new BigDecimal("42"));

    assertThat(roundTrip(amount))
        .isEqualTo(amount);
  }

  /** Through the column and back, the way an entity property reaches the store and returns. */
  private @Nullable Money roundTrip(Money amount) {
    return converter.convertToEntityValue(
        converter.convertToPersistedValue(amount, ConversionContext.DEFAULT),
        ConversionContext.DEFAULT);
  }
}
