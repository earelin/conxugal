package gal.conxugal.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void rejects_null_value() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Money(null));
  }

  @Test
  void keeps_the_published_figure_exactly_as_published() {
    Money amount = new Money(new BigDecimal("3630.00"));

    assertThat(amount.value())
        .hasToString("3630.00");
    assertThat(amount.value().scale())
        .isEqualTo(2);
  }

  @Test
  void keeps_more_decimals_than_two_without_rounding_them_away() {
    Money amount = new Money(new BigDecimal("1234.5678"));

    assertThat(amount.value())
        .hasToString("1234.5678");
    assertThat(amount.value().scale())
        .isEqualTo(4);
  }

  @Test
  void keeps_fewer_decimals_than_two_without_padding_them_out() {
    Money amount = new Money(new BigDecimal("42"));

    assertThat(amount.value())
        .hasToString("42");
    assertThat(amount.value().scale())
        .isZero();
  }

  @Test
  void adds_exactly_where_binary_floating_point_would_drift() {
    Money total = new Money(new BigDecimal("0.1"))
        .plus(new Money(new BigDecimal("0.2")));

    // 0.1d + 0.2d is 0.30000000000000004; exact decimal addition is what stops that reaching a
    // total anybody reads.
    assertThat(total)
        .isEqualTo(new Money(new BigDecimal("0.3")));
  }

  @Test
  void adds_amounts_published_at_different_scales() {
    Money total = new Money(new BigDecimal("3630.00"))
        .plus(new Money(new BigDecimal("0.005")));

    assertThat(total.value())
        .hasToString("3630.005");
  }

  @Test
  void adding_leaves_both_amounts_untouched() {
    Money first = new Money(new BigDecimal("10.00"));
    Money second = new Money(new BigDecimal("5.00"));

    first.plus(second);

    assertThat(first.value())
        .hasToString("10.00");
    assertThat(second.value())
        .hasToString("5.00");
  }

  @Test
  void starts_the_sum_from_nothing_without_disturbing_what_is_added_to_it() {
    Money total = Money.ZERO.plus(new Money(new BigDecimal("3630.00")));

    assertThat(total.value())
        .hasToString("3630.00");
  }

  @Test
  void sums_no_amounts_at_all_into_money_rather_than_into_nothing() {
    assertThat(Money.ZERO.value())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void rejects_adding_null() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Money(BigDecimal.ONE).plus(null));
  }
}
