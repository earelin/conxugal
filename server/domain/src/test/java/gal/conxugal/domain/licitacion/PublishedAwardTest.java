package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The row reduces its own lote cell rather than trusting the parse to have done it, so a caller
 * that hands over a raw cell cannot produce a row that silently fails to join.
 */
class PublishedAwardTest {

  @Test
  void reduces_the_zero_padded_lote_cell_it_was_handed() {
    assertThat(award("05").loteKey()).isEqualTo("5");
  }

  /** Idempotent, so a key that arrives already reduced is left as it is. */
  @Test
  void leaves_the_lote_key_that_was_already_reduced() {
    assertThat(award("5").loteKey()).isEqualTo("5");
  }

  /** The award table writes {@code _}; the shared reduction answers for the others too. */
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"_", "-", "", "   "})
  void holds_the_whole_procedure_for_the_cell_that_names_no_lote(String cell) {
    assertThat(award(cell).loteKey()).isNull();
  }

  @Test
  void answers_nothing_for_the_text_that_carried_only_whitespace() {
    PublishedAward award =
        new PublishedAward(null, "  ", null, null, "\n\t", null, null);

    assertThat(award)
        .extracting(
            PublishedAward::resolution,
            PublishedAward::executionPeriod,
            PublishedAward::awardeeName)
        .containsOnlyNulls();
  }

  /**
   * Text is held exactly as it arrives — the row reduces its lote key and nothing else. Trimming
   * is the parse's, so a double space the source published survives both.
   */
  @Test
  void keeps_the_internal_spacing_the_awardee_name_was_published_with() {
    PublishedAward award =
        new PublishedAward(null, null, null, null, null, "ACME,  S.L.", null);

    assertThat(award.awardeeName()).isEqualTo("ACME,  S.L.");
  }

  private static PublishedAward award(String loteCell) {
    return new PublishedAward(loteCell, null, null, null, null, null, null);
  }
}
