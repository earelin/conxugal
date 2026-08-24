package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.operador.FiscalIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The same reduction the award row applies, which is what makes a formalisation writing
 * {@code 01} meet an award writing {@code 1} without either caller having to know.
 */
class PublishedFormalisationTest {

  @Test
  void reduces_the_zero_padded_lote_cell_it_was_handed() {
    assertThat(formalisation("01").loteKey()).isEqualTo("1");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"_", "-", "", "   "})
  void holds_the_whole_procedure_for_the_cell_that_names_no_lote(String cell) {
    assertThat(formalisation(cell).loteKey()).isNull();
  }

  /**
   * The identifier reduces itself, so a row built from a padded or lower-cased published token
   * still matches the operador the catalogue holds.
   */
  @Test
  void holds_the_identifier_in_the_form_the_catalogue_is_keyed_on() {
    PublishedFormalisation formalisation =
        new PublishedFormalisation(
            null, null, null, new FiscalIdentifier("  b36746584 "), null, null);

    assertThat(formalisation.fiscalIdentifier()).isEqualTo(new FiscalIdentifier("B36746584"));
  }

  /** A formalisation that reached no identifier is an ordinary row, not a rejected one. */
  @Test
  void holds_no_identifier_where_the_split_reached_none() {
    assertThat(formalisation("1").fiscalIdentifier()).isNull();
  }

  @Test
  void answers_nothing_for_the_text_that_carried_only_whitespace() {
    PublishedFormalisation formalisation =
        new PublishedFormalisation(null, null, "  ", null, "\n\t", null);

    assertThat(formalisation)
        .extracting(
            PublishedFormalisation::contratistaName, PublishedFormalisation::nationality)
        .containsOnlyNulls();
  }

  private static PublishedFormalisation formalisation(String loteCell) {
    return new PublishedFormalisation(loteCell, null, null, null, null, null);
  }
}
