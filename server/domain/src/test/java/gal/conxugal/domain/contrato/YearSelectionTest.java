package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class YearSelectionTest {

  private static final List<String> NOT_A_YEAR =
      Arrays.asList(
          null, "", "   ", " 2025 ", "2025 ", "202", "20255", "abcd", "2o25", "-100", "+2025",
          "2025.0", "20 25", "٢٠٢٥");

  private static final List<String> WORDS_FOR_A_LIST_THAT_DOES_NOT_EXIST =
      List.of("all", "undated", "todos", "none");

  @Test
  void answers_with_the_year_it_was_built_for() {
    assertThat(YearSelection.of(2025).year())
        .isEqualTo(2025);
  }

  @Test
  void parses_the_year_its_caller_published() {
    assertThat(YearSelection.parse("1999"))
        .contains(YearSelection.of(1999));
  }

  @Test
  void refuses_anything_that_is_not_exactly_four_digits() {
    assertThat(NOT_A_YEAR)
        .allSatisfy(
            published ->
                assertThat(YearSelection.parse(published))
                    .isEmpty());
  }

  @Test
  void refuses_the_words_an_all_years_or_undated_list_would_be_asked_for_by() {
    assertThat(WORDS_FOR_A_LIST_THAT_DOES_NOT_EXIST)
        .allSatisfy(
            published ->
                assertThat(YearSelection.parse(published))
                    .isEmpty());
  }

  /**
   * Structural, because the requirement is structural: a selection able to represent the absence
   * of a year is a branch every reader would have to remember. One component, and a primitive one,
   * so there is nowhere for a null or a second case to live.
   */
  @Test
  void holds_one_year_and_nothing_beside_it() {
    assertThat(YearSelection.class.getRecordComponents())
        .singleElement()
        .extracting(RecordComponent::getType)
        .isEqualTo(int.class);
  }

  @Test
  void offers_no_constant_standing_for_all_years_or_for_no_year() {
    List<Field> constants =
        Arrays.stream(YearSelection.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> field.getType() == YearSelection.class)
            .toList();

    assertThat(constants)
        .isEmpty();
  }
}
