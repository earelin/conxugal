package gal.conxugal.commons.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WhitespaceTest {

  private static final String NON_BREAKING_SPACE = "\u00a0";
  private static final String EN_QUAD = "\u2000";
  private static final String GROUP_SEPARATOR = "\u001d";
  private static final String NEXT_LINE = "\u0085";

  @Test
  void strips_the_ordinary_surrounding_spaces() {
    assertThat(Whitespace.strip("  Sanidade \t\n")).isEqualTo("Sanidade");
  }

  /** The gap {@link String#strip()} leaves: a non-breaking space is not whitespace to it. */
  @Test
  void strips_non_breaking_spaces_that_string_strip_leaves_behind() {
    String padded = NON_BREAKING_SPACE + EN_QUAD + "Sanidade" + NON_BREAKING_SPACE;

    assertThat(padded.strip()).isEqualTo(padded);
    assertThat(Whitespace.strip(padded)).isEqualTo("Sanidade");
  }

  @Test
  void strips_the_separator_controls_and_the_next_line_character() {
    assertThat(Whitespace.strip(GROUP_SEPARATOR + "Sanidade" + NEXT_LINE)).isEqualTo("Sanidade");
  }

  @Test
  void keeps_the_whitespace_within_the_name() {
    assertThat(Whitespace.strip(" Sanidade e Benestar ")).isEqualTo("Sanidade e Benestar");
  }

  @Test
  void leaves_nothing_of_an_entirely_blank_value() {
    assertThat(Whitespace.strip("  \t" + NON_BREAKING_SPACE + GROUP_SEPARATOR)).isEmpty();
  }

  @Test
  void reads_an_entirely_blank_value_as_blank() {
    assertThat(Whitespace.isBlank("  \t" + NON_BREAKING_SPACE + GROUP_SEPARATOR)).isTrue();
  }

  @Test
  void reads_an_empty_value_as_blank() {
    assertThat(Whitespace.isBlank("")).isTrue();
  }

  @Test
  void reads_padded_name_as_not_blank() {
    assertThat(Whitespace.isBlank(NON_BREAKING_SPACE + "Sanidade ")).isFalse();
  }

  /** The two say the same thing, and a caller may reach for either. */
  @Test
  void is_blank_agrees_with_the_non_blank_pattern() {
    Pattern nonBlank = Pattern.compile(Whitespace.NON_BLANK_PATTERN);

    for (char character = 0; character < Character.MAX_VALUE; character++) {
      String value = String.valueOf(character);
      assertThat(Whitespace.isBlank(value))
          .as("blankness of U+%04X".formatted((int) character))
          .isEqualTo(!nonBlank.matcher(value).matches());
    }
  }

  @Test
  void non_blank_pattern_rejects_exactly_what_strip_empties() {
    Pattern nonBlank = Pattern.compile(Whitespace.NON_BLANK_PATTERN);

    assertThat(nonBlank.matcher("  \t" + NON_BREAKING_SPACE + GROUP_SEPARATOR).matches()).isFalse();
    assertThat(nonBlank.matcher(NON_BREAKING_SPACE + "Sanidade").matches()).isTrue();
    assertThat(nonBlank.matcher("Sanidade\ne Benestar").matches()).isTrue();
  }
}
