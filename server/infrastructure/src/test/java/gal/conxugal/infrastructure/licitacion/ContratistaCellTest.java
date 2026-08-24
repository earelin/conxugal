package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.operador.FiscalIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The split that decides which operador 58% of awards belong to, on the two layouts the source
 * writes the pair in and on every name the captured records publish.
 *
 * <p>The negative cases are the ones that matter. A trailing token wrongly taken for an identifier
 * does not merely lose a name — it catalogues an operador under a fragment of one, and every award
 * that fragment matches thereafter is attributed to it.
 */
class ContratistaCellTest {

  @Test
  void splits_the_cell_the_source_joins_with_one_space() {
    ContratistaCell cell = ContratistaCell.read("EQUINSE, S.A. A41111220");

    assertThat(cell.name()).isEqualTo("EQUINSE, S.A.");
    assertThat(cell.fiscalIdentifier()).isEqualTo(new FiscalIdentifier("A41111220"));
  }

  /**
   * The layout the captured records actually use — a {@code <br>}, which jsoup renders as a
   * newline, with the source's own indentation either side of it. The documented
   * {@code EQUINSE, S.A. A41111220} form is from a procedure no capture holds, so without this
   * case the rule would be tested only against markup nobody serves.
   */
  @Test
  void splits_the_cell_the_markup_breaks_across_lines() {
    ContratistaCell cell = ContratistaCell.read("\n    ESQUEIRO, SL\n    \n\n    B15590581\n");

    assertThat(cell.name()).isEqualTo("ESQUEIRO, SL");
    assertThat(cell.fiscalIdentifier()).isEqualTo(new FiscalIdentifier("B15590581"));
  }

  /** A UTE publishes its own identifier here, and this is the route that finds it. */
  @Test
  void splits_the_consortium_cell_that_carries_its_own_identifier() {
    ContratistaCell cell =
        ContratistaCell.read("UTE CARLOS GARCÍA SAORÍN-MIGUEL JIMÉNEZ MARTÍN U86486669");

    assertThat(cell.name()).isEqualTo("UTE CARLOS GARCÍA SAORÍN-MIGUEL JIMÉNEZ MARTÍN");
    assertThat(cell.fiscalIdentifier()).isEqualTo(new FiscalIdentifier("U86486669"));
  }

  /**
   * Every one of these ends in a token that passes some part of the shape test and must still be
   * read as a name: {@code S.A.} and {@code S.L.U.} carry dots, {@code MARTIN} and
   * {@code COPASA} carry no digits, {@code CONSTRUCCIONES} is long enough and carries none,
   * {@code GRUPO 2000} and {@code ALGO 2024-2026} carry too few or a hyphen, and
   * {@code TEMP-2026-0001} is the placeholder form the source publishes where it has no
   * identifier at all.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "ANGEL CABARCOS ABADIN",
        "SOCIEDAD ANONIMA DE OBRAS Y SERVICIOS COPASA",
        "MISTURAS OBRAS E PROXECTOS, S.A.",
        "QUANTUM ENERGÍA VERDE COMUNIDAD VALENCIANA S.L.U.",
        "OBRAS Y SERVICIOS GÓMEZ CRESPO, S.L.",
        "CONSTRUCCIONES Y OBRAS CONSTRUCCIONES",
        "ALGO 2024-2026",
        "GRUPO 2000",
        "ALGUÉN TEMP-2026-0001"
      })
  void keeps_the_whole_cell_as_the_name_when_its_last_word_only_looks_like_one(String published) {
    ContratistaCell cell = ContratistaCell.read(published);

    assertThat(cell.name()).isEqualTo(published);
    assertThat(cell.fiscalIdentifier()).isNull();
  }

  /**
   * A cell of one token is a name. The rule takes a trailing token and leaves a remainder, and a
   * formalisation carrying an identifier and nobody's name is not something the source publishes —
   * so the safe reading of an unsplittable cell is the one that invents no party.
   */
  @Test
  void keeps_the_whole_cell_as_the_name_when_the_source_published_only_one_token() {
    ContratistaCell cell = ContratistaCell.read("B36746584");

    assertThat(cell.name()).isEqualTo("B36746584");
    assertThat(cell.fiscalIdentifier()).isNull();
  }

  /** Irregular but genuine identifiers resolve: rejecting them would discard real awards. */
  @Test
  void takes_the_foreign_identifier_the_source_appends() {
    ContratistaCell cell = ContratistaCell.read("SOMETHING LDA PT501234567");

    assertThat(cell.name()).isEqualTo("SOMETHING LDA");
    assertThat(cell.fiscalIdentifier()).isEqualTo(new FiscalIdentifier("PT501234567"));
  }

  /** The name is trimmed at its ends and nowhere else, so a published double space survives. */
  @Test
  void keeps_the_internal_spacing_the_name_was_published_with() {
    ContratistaCell cell = ContratistaCell.read("  CIVIS  GLOBAL, S.L. B12345678  ");

    assertThat(cell.name()).isEqualTo("CIVIS  GLOBAL, S.L.");
  }

  @Test
  void answers_nothing_for_the_cell_that_is_empty_once_trimmed() {
    ContratistaCell cell = ContratistaCell.read("   ");

    assertThat(cell.name()).isNull();
    assertThat(cell.fiscalIdentifier()).isNull();
  }
}
