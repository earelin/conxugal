package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublishedTextTest {

  private static final String NON_BREAKING_SPACE = "\u00a0";

  @Test
  void keeps_the_published_value_exactly_as_it_was_handed_over() {
    assertThat(PublishedText.orNullWhenBlank("Obra civil"))
        .isEqualTo("Obra civil");
  }

  @Test
  void keeps_the_surrounding_whitespace_that_the_natural_key_would_have_stripped() {
    // The one assertion that tells this rule apart from PublishedKey's. Ordinary text is stored
    // byte-for-byte as published: the adapter has already trimmed what the markup added, so
    // anything still here is the source's own.
    assertThat(PublishedText.orNullWhenBlank("  Obra civil \t"))
        .isEqualTo("  Obra civil \t");
  }

  @Test
  void keeps_the_internal_spacing_and_the_letter_case() {
    assertThat(PublishedText.orNullWhenBlank("Servizos   ESPECIALIZADOS"))
        .isEqualTo("Servizos   ESPECIALIZADOS");
  }

  @Test
  void answers_null_for_the_value_that_published_nothing() {
    assertThat(PublishedText.orNullWhenBlank(null))
        .isNull();
  }

  @Test
  void answers_null_for_the_empty_value() {
    assertThat(PublishedText.orNullWhenBlank(""))
        .isNull();
  }

  @Test
  void answers_null_for_the_value_that_carried_only_whitespace() {
    assertThat(PublishedText.orNullWhenBlank(" \t\n"))
        .isNull();
  }

  @Test
  void answers_null_for_the_value_that_carried_only_non_breaking_space() {
    // The gap String.isBlank leaves, and why the check goes through Whitespace: a non-breaking
    // space in the record's markup is not whitespace to the JDK, so a cell carrying one would be
    // stored as a published value rather than as the absence of one.
    assertThat(PublishedText.orNullWhenBlank(NON_BREAKING_SPACE))
        .isNull();
  }

  @Test
  void keeps_the_value_whose_padding_is_non_breaking_but_whose_content_is_not_blank() {
    String padded = NON_BREAKING_SPACE + "Obra civil" + NON_BREAKING_SPACE;

    assertThat(PublishedText.orNullWhenBlank(padded))
        .isEqualTo(padded);
  }
}
