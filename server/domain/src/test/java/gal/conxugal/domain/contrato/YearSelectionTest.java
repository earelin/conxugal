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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class YearSelectionTest {

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

  @ParameterizedTest
  @ValueSource(ints = {1000, 2025, 9999})
  void parses_the_years_at_both_ends_of_what_it_can_be_built_with(int year) {
    assertThat(YearSelection.parse(String.valueOf(year)))
        .contains(YearSelection.of(year));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        " 2025 ",
        "2025 ",
        "2025\n",
        "202",
        "20255",
        "0000",
        "0999",
        "abcd",
        "2o25",
        "-100",
        "+2025",
        "2025.0",
        "20 25",
        "٢٠٢٥"
      })
  void refuses_anything_that_is_not_exactly_four_digits(String published) {
    assertThat(YearSelection.parse(published))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"all", "undated", "todos", "none"})
  void refuses_the_words_an_all_years_or_undated_list_would_be_asked_for_by(String published) {
    assertThat(YearSelection.parse(published))
        .isEmpty();
  }

  /** Building one directly and parsing one admit the same set, or the type's claim is untrue. */
  @ParameterizedTest
  @ValueSource(ints = {0, -5, 999, 10_000, Integer.MAX_VALUE})
  void refuses_to_be_built_from_something_no_publication_could_carry(int year) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> YearSelection.of(year));
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
    assertThat(staticMembersYieldingSelections())
        .extracting(Member::getName)
        .containsExactly("of");
  }

  /**
   * Every way the type hands out a selection without being given one: a constant, or a factory.
   * Both kinds are collected because the criterion refuses both, and an absent-year constant would
   * be as reachable as an absent-year factory.
   */
  private static List<Member> staticMembersYieldingSelections() {
    return Stream.concat(
            Arrays.stream(YearSelection.class.getDeclaredFields())
                .filter(field -> field.getType() == YearSelection.class),
            Arrays.stream(YearSelection.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == YearSelection.class))
        .filter(member -> Modifier.isStatic(member.getModifiers()))
        .map(Member.class::cast)
        .toList();
  }
}
