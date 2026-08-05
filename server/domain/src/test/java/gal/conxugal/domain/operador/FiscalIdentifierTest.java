package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FiscalIdentifierTest {

  private static final String NON_BREAKING_SPACE = "\u00a0";
  private static final String GROUP_SEPARATOR = "\u001d";

  @Test
  void identifiers_differing_only_in_padding_and_case_reduce_to_one_value() {
    assertThat(FiscalIdentifier.canonical(" b12345678 "))
        .isEqualTo(FiscalIdentifier.canonical("B12345678 "))
        .isEqualTo(FiscalIdentifier.canonical("b12345678"))
        .contains("B12345678");
  }

  @Test
  void the_canonical_form_is_upper_cased_rather_than_lower_cased() {
    assertThat(FiscalIdentifier.canonical("b12345678a")).contains("B12345678A");
  }

  @Test
  void canonicalising_an_already_canonical_identifier_leaves_it_unchanged() {
    String once = FiscalIdentifier.canonical(" b12345678 ").orElseThrow();

    assertThat(FiscalIdentifier.canonical(once)).contains(once);
  }

  @Test
  void internal_spacing_makes_another_identifier_and_survives_in_the_result() {
    assertThat(FiscalIdentifier.canonical("b1234 5678"))
        .contains("B1234 5678")
        .isNotEqualTo(FiscalIdentifier.canonical("b12345678"));
  }

  @Test
  void punctuation_makes_another_identifier_and_survives_in_the_result() {
    assertThat(FiscalIdentifier.canonical("b-12345678"))
        .contains("B-12345678")
        .isNotEqualTo(FiscalIdentifier.canonical("b12345678"));
  }

  @Test
  void one_differing_character_makes_another_identifier() {
    assertThat(FiscalIdentifier.canonical("B12345679"))
        .isNotEqualTo(FiscalIdentifier.canonical("B12345678"));
  }

  @Test
  void an_absent_identifier_is_unusable() {
    assertThat(FiscalIdentifier.canonical(null)).isEmpty();
  }

  @Test
  void an_empty_identifier_is_unusable() {
    assertThat(FiscalIdentifier.canonical("")).isEmpty();
  }

  @Test
  void whitespace_only_identifier_is_unusable() {
    assertThat(FiscalIdentifier.canonical("  \t\n")).isEmpty();
  }

  @Test
  void an_identifier_of_blanks_no_narrower_rule_would_strip_is_unusable() {
    assertThat(FiscalIdentifier.canonical(NON_BREAKING_SPACE + GROUP_SEPARATOR)).isEmpty();
  }

  @Test
  void padding_no_narrower_rule_would_strip_is_still_padding() {
    assertThat(FiscalIdentifier.canonical(NON_BREAKING_SPACE + "b12345678" + GROUP_SEPARATOR))
        .contains("B12345678");
  }

  @Test
  void foreign_vat_number_is_usable_and_reduces_like_any_other() {
    assertThat(FiscalIdentifier.canonical(" pt501442600 ")).contains("PT501442600");
  }

  @Test
  void malformed_nif_is_usable_and_reduces_like_any_other() {
    assertThat(FiscalIdentifier.canonical(" b1234 ")).contains("B1234");
  }
}
