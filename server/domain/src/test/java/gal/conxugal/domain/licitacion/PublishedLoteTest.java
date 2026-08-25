package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one place the two spellings of a lote are both held: the identifier the source printed, and
 * the reduction everything is compared on. Reduction is for comparison and never for storage,
 * which is what lets a row spelling the same lote {@code 5} find the one stored as {@code 05}.
 */
class PublishedLoteTest {

  @Test
  void holds_the_identifier_as_the_source_spelled_it() {
    assertThat(lote("05").identifier()).isEqualTo("05");
  }

  @Test
  void answers_the_reduced_form_the_other_tables_are_matched_on() {
    assertThat(lote("05").key()).isEqualTo("5");
  }

  /** Not always a number: these were all observed in real award-table lote cells. */
  @ParameterizedTest
  @ValueSource(strings = {"OU0028", "LU4001", "CO0642"})
  void holds_the_lote_identifier_that_is_not_numeric(String identifier) {
    assertThat(lote(identifier).identifier()).isEqualTo(identifier);
  }

  /**
   * A lote standing for the procedure as a whole is a contradiction, and refusing it here is what
   * lets every caller reduce this identifier to a key with no absent branch to handle.
   */
  @ParameterizedTest
  @ValueSource(strings = {"_", "-"})
  void refuses_the_identifier_that_names_the_whole_procedure(String identifier) {
    assertThatThrownBy(() -> lote(identifier)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refuses_the_identifier_that_is_empty_once_trimmed() {
    assertThatThrownBy(() -> lote("   ")).isInstanceOf(IllegalArgumentException.class);
  }

  /** A cell of nothing but zeros is the lote {@code 0}, never the procedure as a whole. */
  @Test
  void holds_the_lote_whose_identifier_is_nothing_but_zeros() {
    assertThat(lote("000").key()).isEqualTo("0");
  }

  /** Both extras are absent whenever the lotes table publishes nothing about the lote. */
  @Test
  void holds_the_lote_the_lotes_table_published_nothing_about() {
    PublishedLote lote = new PublishedLote("1", "  ", null);

    assertThat(lote.description()).isNull();
    assertThat(lote.estimatedValue()).isNull();
  }

  @Test
  void holds_the_estimated_value_the_lotes_table_published() {
    PublishedLote lote = new PublishedLote("1", "Obra civil", new Money(new BigDecimal("900.00")));

    assertThat(lote.estimatedValue()).isEqualTo(new Money(new BigDecimal("900.00")));
  }

  private static PublishedLote lote(String identifier) {
    return new PublishedLote(identifier, null, null);
  }
}
