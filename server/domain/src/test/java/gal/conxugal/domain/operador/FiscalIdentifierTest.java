package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FiscalIdentifierTest {

  private static final String NON_BREAKING_SPACE = "\u00a0";
  private static final String GROUP_SEPARATOR = "\u001d";
  private static final String EN_DASH = "\u2013"; // –
  private static final String EM_DASH = "\u2014"; // —
  private static final String NON_BREAKING_HYPHEN = "\u2011"; // ‑
  private static final String MINUS_SIGN = "\u2212"; // −

  @Test
  void identifiers_differing_only_in_padding_and_case_are_one_identifier() {
    FiscalIdentifier padded = new FiscalIdentifier(" b12345678 ");
    FiscalIdentifier upper = new FiscalIdentifier("B12345678 ");
    FiscalIdentifier bare = new FiscalIdentifier("b12345678");

    assertThat(new HashSet<>(List.of(padded, upper, bare))).hasSize(1);
    assertThat(padded)
        .isEqualTo(upper)
        .isEqualTo(bare)
        .hasToString("B12345678");
  }

  @Test
  void the_canonical_form_is_upper_cased_rather_than_lower_cased() {
    assertThat(new FiscalIdentifier("b12345678a").value()).isEqualTo("B12345678A");
  }

  @Test
  void rebuilding_an_identifier_from_its_own_value_leaves_it_unchanged() {
    FiscalIdentifier once = new FiscalIdentifier(" b12345678 ");

    assertThat(new FiscalIdentifier(once.value())).isEqualTo(once);
  }

  @Test
  void internal_spacing_makes_another_identifier_and_survives_in_the_value() {
    FiscalIdentifier spaced = new FiscalIdentifier("b1234 5678");

    assertThat(spaced.value()).isEqualTo("B1234 5678");
    assertThat(spaced).isNotEqualTo(new FiscalIdentifier("b12345678"));
  }

  @Test
  void punctuation_makes_another_identifier_and_survives_in_the_value() {
    FiscalIdentifier punctuated = new FiscalIdentifier("b-12345678");

    assertThat(punctuated.value()).isEqualTo("B-12345678");
    assertThat(punctuated).isNotEqualTo(new FiscalIdentifier("b12345678"));
  }

  @Test
  void one_differing_character_makes_another_identifier() {
    assertThat(new FiscalIdentifier("B12345679"))
        .isNotEqualTo(new FiscalIdentifier("B12345678"));
  }

  @Test
  void padding_no_narrower_rule_would_strip_is_still_padding() {
    FiscalIdentifier padded =
        new FiscalIdentifier(NON_BREAKING_SPACE + " b12345678" + GROUP_SEPARATOR);

    assertThat(padded).isEqualTo(new FiscalIdentifier("B12345678"));
  }

  @Test
  void rejects_null_rather_than_holding_no_identifier() {
    assertThatNullPointerException().isThrownBy(() -> new FiscalIdentifier(null));
  }

  @Test
  void rejects_an_identifier_that_is_empty_once_trimmed() {
    assertThatIllegalArgumentException().isThrownBy(() -> new FiscalIdentifier("  \t\n"));
  }

  @Test
  void rejects_an_empty_identifier() {
    assertThatIllegalArgumentException().isThrownBy(() -> new FiscalIdentifier(""));
  }

  // The canonical constructor is deliberately permissive about placeholders, in both spellings a
  // store may already hold: a row persisted under one before FiscalIdentifier.of turned them away
  // has to read back rather than fail the read that finds it.
  @ParameterizedTest
  @ValueSource(strings = {"-", "TEMP-00934"})
  void constructs_the_placeholder_so_stored_row_reads_back(String persisted) {
    assertThat(new FiscalIdentifier(persisted).value()).isEqualTo(persisted);
  }

  @Test
  void an_absent_published_identifier_is_unusable() {
    assertThat(FiscalIdentifier.of(null)).isEmpty();
  }

  @Test
  void an_empty_published_identifier_is_unusable() {
    assertThat(FiscalIdentifier.of("")).isEmpty();
  }

  @Test
  void whitespace_only_published_identifier_is_unusable() {
    assertThat(FiscalIdentifier.of("  \t\n")).isEmpty();
  }

  @Test
  void published_blanks_no_narrower_rule_would_strip_are_unusable() {
    assertThat(FiscalIdentifier.of(NON_BREAKING_SPACE + GROUP_SEPARATOR)).isEmpty();
  }

  // Every spelling of a lone dash is the same placeholder. The rule reads the canonical form, so
  // padding and case cannot smuggle one past it, and a dash the source spells typographically has
  // to count as one too or the pooling this refuses returns under another spelling. The record
  // page is ISO-8859-1 and cannot carry one; the listing is JSON and is under no such limit.
  @ParameterizedTest
  @ValueSource(strings = {"-", " - ", EN_DASH, EM_DASH, NON_BREAKING_HYPHEN, MINUS_SIGN})
  void published_lone_dash_is_unusable_however_it_is_spelled(String published) {
    assertThat(FiscalIdentifier.of(published)).isEmpty();
  }

  // Likewise the temporary form, whose dash is subject to the same spellings and whose letters
  // reach the rule already upper-cased.
  @ParameterizedTest
  @ValueSource(strings = {"TEMP-00934", "temp-00934", "TEMP" + EN_DASH + "00934"})
  void published_temporary_placeholder_is_unusable_however_it_is_spelled(String published) {
    assertThat(FiscalIdentifier.of(published)).isEmpty();
  }

  // The rule rejects the two published placeholder forms and nothing else: an identifier that
  // merely carries a dash is irregular, not absent, and discarding it would discard real awards.
  @Test
  void identifier_merely_carrying_dash_is_usable_and_reduces_like_any_other() {
    assertThat(FiscalIdentifier.of(" x-1234567z "))
        .contains(new FiscalIdentifier("X-1234567Z"));
  }

  @Test
  void published_identifier_reduces_on_the_way_through() {
    assertThat(FiscalIdentifier.of(" b12345678 ")).contains(new FiscalIdentifier("B12345678"));
  }

  @Test
  void foreign_vat_number_is_usable_and_reduces_like_any_other() {
    assertThat(FiscalIdentifier.of(" pt501442600 "))
        .contains(new FiscalIdentifier("PT501442600"));
  }

  @Test
  void malformed_nif_is_usable_and_reduces_like_any_other() {
    assertThat(FiscalIdentifier.of(" b1234 ")).contains(new FiscalIdentifier("B1234"));
  }
}
