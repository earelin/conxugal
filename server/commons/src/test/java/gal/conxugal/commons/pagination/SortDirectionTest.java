package gal.conxugal.commons.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SortDirectionTest {

  private static final List<String> NOT_A_DIRECTION =
      Arrays.asList(
          null, "", "   ", "ascending", "descending", "ASC", "DESC", "Asc", "Desc", "up", "down",
          " asc", "desc ");

  @Test
  void parses_the_ascending_direction_the_contract_publishes_it_as() {
    assertThat(SortDirection.parse("asc"))
        .contains(SortDirection.ASC);
  }

  @Test
  void parses_the_descending_direction_the_contract_publishes_it_as() {
    assertThat(SortDirection.parse("desc"))
        .contains(SortDirection.DESC);
  }

  /**
   * The one that matters most: a direction that degraded to ascending when it was not recognised
   * would serve one ordering under the label of another, which nothing downstream could detect.
   */
  @Test
  void refuses_every_other_spelling_rather_than_degrading_to_ascending() {
    assertThat(NOT_A_DIRECTION)
        .allSatisfy(
            published ->
                assertThat(SortDirection.parse(published))
                    .isEmpty());
  }

  @Test
  void offers_the_two_directions_and_no_third() {
    assertThat(SortDirection.values())
        .containsExactly(SortDirection.ASC, SortDirection.DESC);
  }
}
