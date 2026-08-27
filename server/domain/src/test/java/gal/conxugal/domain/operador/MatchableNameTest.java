package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/** What the fold two published names are compared on keeps, and what it deliberately loses. */
class MatchableNameTest {

  @Test
  void folds_case_accents_and_punctuation_so_two_spellings_of_one_name_compare_equal() {
    assertThat(MatchableName.of("Xestión Ambiental de Contratas, S.L."))
        .contains(MatchableName.of("XESTION AMBIENTAL DE CONTRATAS SL").orElseThrow());
  }

  @Test
  void folds_surrounding_and_internal_spacing_including_the_space_stripping_overlooks() {
    assertThat(MatchableName.of("  EQUINSE,   S.A. "))
        .contains(MatchableName.of("EQUINSE, S.A.").orElseThrow());
  }

  @Test
  void keeps_the_digits_that_tell_two_similarly_named_firms_apart() {
    assertThat(MatchableName.of("GRUPO 2000 SL"))
        .isNotEqualTo(MatchableName.of("GRUPO 3000 SL"));
  }

  @Test
  void keeps_every_letter_so_two_different_names_do_not_fold_together() {
    assertThat(MatchableName.of("ESQUEIRO, SL"))
        .isNotEqualTo(MatchableName.of("ESQUEIROS, SL"));
  }

  @Test
  void has_no_key_for_an_absent_name() {
    assertThat(MatchableName.of(null)).isEmpty();
  }

  // Two names that fold to nothing are not evidence that they name the same party, so there is no
  // key for them rather than one that would match every other such name.
  @Test
  void has_no_key_for_name_nothing_survives_the_fold_of() {
    assertThat(MatchableName.of("  ")).isEmpty();
    assertThat(MatchableName.of("--- ,.")).isEmpty();
  }

  @Test
  void refuses_to_be_built_holding_key_nothing_could_match() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new MatchableName(""));
  }

  @Test
  void reads_as_the_key_it_holds() {
    assertThat(MatchableName.of("Xestión Ambiental, S.L.").orElseThrow())
        .hasToString("xestionambientalsl");
  }
}
