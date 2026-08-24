package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The keyed half is required; everything the source may leave out is not. */
class PublishedCpvClassificationTest {

  @Test
  void strips_the_padding_around_the_published_code() {
    assertThat(classification("  45000000  ").code()).isEqualTo("45000000");
  }

  /**
   * A classification keyed on nothing names nothing, and the vocabulary is unique on the code, so
   * a row that reached one cannot be built. A cell the source left empty is dropped by the parse
   * instead, which is a different thing from refusing the record.
   */
  @Test
  void refuses_the_code_that_is_empty_once_trimmed() {
    assertThatThrownBy(() -> classification("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * The departure amendment 2 legitimises: a null lote means <em>the procedure as a whole</em>,
   * and it is what the source publishes even on a procedure that has lotes.
   */
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"_", "-", "", "   "})
  void holds_the_whole_procedure_for_the_cell_that_names_no_lote(String cell) {
    PublishedCpvClassification classification =
        new PublishedCpvClassification("45000000", null, cell, null);

    assertThat(classification.loteKey()).isNull();
  }

  @Test
  void reduces_the_zero_padded_lote_cell_it_was_handed() {
    PublishedCpvClassification classification =
        new PublishedCpvClassification("45000000", null, "03", null);

    assertThat(classification.loteKey()).isEqualTo("3");
  }

  /** Nothing matches on the wording, so a row that reached none is a whole classification. */
  @Test
  void answers_nothing_for_the_wording_that_carried_only_whitespace() {
    PublishedCpvClassification classification =
        new PublishedCpvClassification("45000000", "  ", null, null);

    assertThat(classification.description()).isNull();
  }

  private static PublishedCpvClassification classification(String code) {
    return new PublishedCpvClassification(code, null, null, null);
  }
}
