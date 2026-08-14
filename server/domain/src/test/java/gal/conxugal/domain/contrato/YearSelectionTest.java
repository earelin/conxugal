package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class YearSelectionTest {

  private static final List<String> NOT_A_YEAR =
      Arrays.asList(
          null, "", "   ", " 2025 ", "2025 ", "2025\n", "202", "20255", "0000", "0999", "abcd",
          "2o25", "-100", "+2025", "2025.0", "20 25", "٢٠٢٥");

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

  /** Building one directly and parsing one admit the same set, or the type's claim is untrue. */
  @Test
  void refuses_to_be_built_from_something_no_publication_could_carry() {
    assertThat(List.of(0, -5, 999, 10_000, Integer.MAX_VALUE))
        .allSatisfy(
            year ->
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> YearSelection.of(year)));
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

  /** {@code of} is the only way in, and it takes a year — so no way in yields an absent one. */
  @Test
  void offers_no_other_factory_or_constant_standing_for_all_years() {
    Stream<Member> yieldingSelections =
        Stream.concat(
            Arrays.stream(YearSelection.class.getDeclaredFields())
                .filter(field -> field.getType() == YearSelection.class),
            Arrays.stream(YearSelection.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == YearSelection.class));

    assertThat(yieldingSelections.filter(member -> Modifier.isStatic(member.getModifiers())))
        .extracting(Member::getName)
        .containsExactly("of");
  }

  @Test
  void parses_every_year_it_can_be_built_with_and_no_other() {
    assertThat(List.of(1000, 2025, 9999))
        .allSatisfy(
            year ->
                assertThat(YearSelection.parse(String.valueOf(year)))
                    .contains(YearSelection.of(year)));
  }
}
