package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class PublishedKeyTest {

  private static final String NON_BREAKING_SPACE = "\u00a0";

  @Test
  void leaves_an_already_canonical_value_alone() {
    assertThat(PublishedKey.canonical("Obras", "name"))
        .isEqualTo("Obras");
  }

  @Test
  void strips_the_surrounding_whitespace_that_would_key_the_second_row() {
    assertThat(PublishedKey.canonical("  Obras \t\n", "name"))
        .isEqualTo("Obras");
  }

  /**
   * The gap {@link String#strip()} leaves, and why the reduction goes through
   * {@link gal.conxugal.commons.text.Whitespace}: a non-breaking space in the record's markup is
   * not whitespace to {@code strip}, so the padded value would key its own row.
   */
  @Test
  void strips_the_non_breaking_space_that_string_strip_leaves_behind() {
    String padded = NON_BREAKING_SPACE + "Obras" + NON_BREAKING_SPACE;

    assertThat(padded.strip()).isEqualTo(padded);
    assertThat(PublishedKey.canonical(padded, "name"))
        .isEqualTo("Obras");
  }

  @Test
  void reduces_no_further_than_the_ends() {
    String published = "Negociado   sen publicidade, art. 168";

    assertThat(PublishedKey.canonical(" %s ".formatted(published), "name"))
        .isEqualTo(published);
  }

  @Test
  void keeps_two_published_spellings_apart_rather_than_folding_their_case() {
    assertThat(PublishedKey.canonical("Obras", "name"))
        .isNotEqualTo(PublishedKey.canonical("obras", "name"));
  }

  @Test
  void refuses_the_absent_value() {
    assertThatNullPointerException()
        .isThrownBy(() -> PublishedKey.canonical(null, "publicationId"))
        .withMessage("publicationId must not be null");
  }

  @Test
  void refuses_the_empty_value() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PublishedKey.canonical("", "publicationId"))
        .withMessage("publicationId must not be empty once trimmed");
  }

  @Test
  void refuses_the_value_that_is_empty_once_stripped() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PublishedKey.canonical(" \t" + NON_BREAKING_SPACE, "name"))
        .withMessage("name must not be empty once trimmed");
  }

  @Test
  void names_the_component_it_was_given_rather_than_one_of_its_callers() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PublishedKey.canonical("  ", "publicationId"))
        .withMessageStartingWith("publicationId ");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PublishedKey.canonical("  ", "name"))
        .withMessageStartingWith("name ");
  }
}
