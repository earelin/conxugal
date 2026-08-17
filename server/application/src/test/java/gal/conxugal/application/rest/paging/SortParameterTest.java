package gal.conxugal.application.rest.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.data.model.Sort;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zalando.problem.ThrowableProblem;

class SortParameterTest {

  @Test
  void ascending_parameter_is_split_into_its_property_and_its_direction() {
    SortParameter parameter = SortParameter.of("publicationDate,asc");

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(parameter.property()).isEqualTo("publicationDate");
      softly.assertThat(parameter.direction()).isEqualTo(Sort.Order.Direction.ASC);
    });
  }

  @Test
  void descending_parameter_is_split_into_its_property_and_its_direction() {
    SortParameter parameter = SortParameter.of("amount,desc");

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(parameter.property()).isEqualTo("amount");
      softly.assertThat(parameter.direction()).isEqualTo(Sort.Order.Direction.DESC);
    });
  }

  // The property comes back as text on purpose. Which names an operation offers is its own closed
  // set, and matching against that set is what keeps a caller's text out of an ORDER BY a native
  // statement appends verbatim — a shared type accepting any property would look like validation
  // while providing none.
  @Test
  void property_is_handed_back_unjudged_for_the_operation_to_match() {
    assertThat(SortParameter.of("whateverTheCallerWrote,asc").property())
        .isEqualTo("whateverTheCallerWrote");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "amount", "publicationDate", "amount,asc,desc", ",,"})
  void parameter_that_is_not_property_comma_direction_is_refused(String sort) {
    assertThatThrownBy(() -> SortParameter.of(sort))
        .isInstanceOf(ThrowableProblem.class)
        .hasMessageContaining("sort must be property,direction");
  }

  // Micronaut's own binder turns an unrecognised direction into ascending, which would answer a
  // different ordering under the label the caller asked for — and the envelope states no ordering
  // back, so nothing on the wire would reveal it.
  @ParameterizedTest
  @ValueSource(strings = {"amount,descending", "amount,ASC", "amount,up", "amount, asc", "amount,",
      ","})
  void direction_outside_the_two_offered_is_refused_rather_than_degraded(String sort) {
    assertThatThrownBy(() -> SortParameter.of(sort))
        .isInstanceOf(ThrowableProblem.class)
        .hasMessageContaining("sort direction must be asc or desc");
  }

  @Test
  void empty_property_is_left_to_the_operation_rather_than_refused_here() {
    assertThat(SortParameter.of(",asc").property()).isEmpty();
  }
}
