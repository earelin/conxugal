package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The CPV row's rules, on the vocabulary where a mismatch is not a visible error but a procedure
 * classified by a region as though it were a purpose. Two records rather than one is what makes
 * handing the wrong list over a compile error, so both are covered rather than one standing in.
 */
class PublishedNutClassificationTest {

  @Test
  void strips_the_padding_around_the_published_code() {
    assertThat(new PublishedNutClassification("  ES111  ", null, null, null).code())
        .isEqualTo("ES111");
  }

  @Test
  void refuses_the_code_that_is_empty_once_trimmed() {
    assertThatThrownBy(() -> new PublishedNutClassification("   ", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** The procedure-wide row is the ordinary case here: 217 of 240 measured rows write {@code _}. */
  @Test
  void holds_the_whole_procedure_for_the_cell_that_names_no_lote() {
    assertThat(new PublishedNutClassification("ES111", null, "_", null).loteKey()).isNull();
  }

  @Test
  void reduces_the_zero_padded_lote_cell_it_was_handed() {
    assertThat(new PublishedNutClassification("ES111", null, "08", null).loteKey())
        .isEqualTo("8");
  }
}
