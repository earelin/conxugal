package gal.conxugal.application.rest.request;

import static gal.conxugal.application.rest.request.QueryValues.wholeNumberOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zalando.problem.ThrowableProblem;

class QueryValuesTest {

  @Test
  void parameter_that_was_not_sent_answers_with_its_default() {
    assertThat(wholeNumberOf("size", null, 50)).isEqualTo(50);
  }

  // The whole reason this exists: bound as an int with a defaultValue, an empty value fell back to
  // the default instead of being refused, so ?size= was answered with fifty rows and a body saying
  // it had been asked for fifty. Absent and empty are different requests.
  @Test
  void parameter_sent_empty_is_refused_rather_than_defaulted() {
    assertThatThrownBy(() -> wholeNumberOf("size", "", 50))
        .isInstanceOf(ThrowableProblem.class)
        .hasMessageContaining("size must be a whole number");
  }

  @Test
  void parameter_holding_digits_answers_with_that_number() {
    assertThat(wholeNumberOf("page", "3", 1)).isEqualTo(3);
  }

  @Test
  void negative_number_reaches_the_caller_to_refuse_on_its_own_terms() {
    assertThat(wholeNumberOf("page", "-1", 1)).isEqualTo(-1);
  }

  @Test
  void widest_value_an_int32_holds_is_answered_rather_than_wrapped() {
    assertThat(wholeNumberOf("page", "2147483647", 1)).isEqualTo(Integer.MAX_VALUE);
  }

  // Not a number, or wider than the int32 the contract declares. parseInt would have taken the
  // sign and the Arabic-Indic digits, and would have wrapped nothing — it throws — but the pattern
  // is what makes the accepted set the one the contract describes rather than the one Java parses.
  @ParameterizedTest
  @ValueSource(strings = {"first", "3.5", "+50", " 3", "3 ", "٣", "2147483648", "99999999999",
      "-2147483649"})
  void anything_the_contract_does_not_offer_is_refused(String published) {
    assertThatThrownBy(() -> wholeNumberOf("page", published, 1))
        .isInstanceOf(ThrowableProblem.class)
        .hasMessageContaining("page must be a whole number");
  }

  @Test
  void refusal_names_the_parameter_it_is_about() {
    assertThatThrownBy(() -> wholeNumberOf("size", "nope", 50))
        .hasMessageContaining("size must be");
  }
}
