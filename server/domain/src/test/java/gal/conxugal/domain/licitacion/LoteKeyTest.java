package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoteKeyTest {

  @ParameterizedTest
  @ValueSource(strings = {"_", "-", "", " ", "\t", "  \t "})
  void reads_every_procedure_wide_spelling_as_the_procedure_as_whole(String cell) {
    // The award, formalisation and NUT tables write `_`; the bidder table writes `-`. A parse
    // hard-coding either loses the other, and the join it feeds fails on rows that agree.
    assertThat(LoteKey.normalise(cell))
        .isEmpty();
  }

  @Test
  void reads_an_absent_cell_as_the_procedure_as_whole() {
    assertThat(LoteKey.normalise(null))
        .isEmpty();
  }

  @Test
  void collapses_the_paddings_of_one_lote_onto_one_key() {
    // Zero-padding varies within a table rather than between them: the award table produced both
    // `1` and `05` in one sample, so `01` and `1` are the same lote wherever either is read.
    assertThat(LoteKey.normalise("01"))
        .contains("1")
        .isEqualTo(LoteKey.normalise("1"))
        .isEqualTo(LoteKey.normalise(" 1 "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"})
  void leaves_an_unpadded_numbered_lote_exactly_as_published(String cell) {
    assertThat(LoteKey.normalise(cell))
        .contains(cell);
  }

  @ParameterizedTest
  @ValueSource(strings = {"01", "02", "03", "05"})
  void strips_the_leading_zero_from_every_padded_spelling_measured(String cell) {
    assertThat(LoteKey.normalise(cell))
        .contains(cell.substring(1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"OU0028", "LU4001", "LU4031", "CO0642"})
  void leaves_an_alphanumeric_lote_intact(String cell) {
    // All four were observed in award-table lote cells, which is why a lote is text and why
    // nothing here parses one as a number.
    assertThat(LoteKey.normalise(cell))
        .contains(cell);
  }

  @Test
  void strips_the_padding_from_an_alphanumeric_lote_without_touching_its_interior_zeros() {
    assertThat(LoteKey.normalise("  OU0028\t"))
        .contains("OU0028");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "00", "000"})
  void keeps_lote_of_nothing_but_zeros_as_published_lote_rather_than_procedure_wide(String cell) {
    // Stripping every zero would turn a published lote into the absence of one, which is a
    // different fact and would file its award against the procedure.
    assertThat(LoteKey.normalise(cell))
        .contains("0");
  }

  @Test
  void keeps_two_lotes_differing_only_in_letter_case_apart() {
    // Nothing beyond padding is reduced: folding case would assert an equivalence the source
    // never published.
    assertThat(LoteKey.normalise("OU0028"))
        .isNotEqualTo(LoteKey.normalise("ou0028"));
  }

  @Test
  void keeps_the_dash_inside_lote_identifier_rather_than_reading_it_as_procedure_wide() {
    // Only a cell that is exactly the marker means the procedure as a whole; a dash occurring
    // inside an identifier is part of it.
    assertThat(LoteKey.normalise("A-1"))
        .contains("A-1");
  }
}
