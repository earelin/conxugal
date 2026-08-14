package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectionTest {

  private static final List<String> NOT_A_DIRECTION =
      Arrays.asList(
          null, "", "   ", "ascending", "descending", "ASC", "DESC", "Asc", "Desc", "up", "down",
          " asc", "desc ");

  @Test
  void parses_the_ascending_direction_the_contract_publishes_it_as() {
    assertThat(Direction.parse("asc"))
        .contains(Direction.ASC);
  }

  @Test
  void parses_the_descending_direction_the_contract_publishes_it_as() {
    assertThat(Direction.parse("desc"))
        .contains(Direction.DESC);
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
                assertThat(Direction.parse(published))
                    .isEmpty());
  }

  @Test
  void offers_the_two_directions_and_no_third() {
    assertThat(Direction.values())
        .containsExactly(Direction.ASC, Direction.DESC);
  }
}
