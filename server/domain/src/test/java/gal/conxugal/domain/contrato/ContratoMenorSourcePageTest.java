package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContratoMenorSourcePageTest {

  private static final long RECORDS_TOTAL = 14822L;

  /**
   * The message is asserted because {@code List.copyOf} would throw a bare, unexplained
   * {@link NullPointerException} on its own — so without it this would pass with the explicit
   * check deleted.
   */
  @Test
  void rejects_null_entries() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ContratoMenorSourcePage(null, RECORDS_TOTAL))
        .withMessageContaining("entries");
  }

  @Test
  void rejects_negative_records_total() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContratoMenorSourcePage(List.of(entry(2001090L)), -1));
  }

  /** An Órgano that has published nothing at all is an ordinary answer, not a failure. */
  @Test
  void accepts_records_total_of_zero() {
    ContratoMenorSourcePage page = new ContratoMenorSourcePage(List.of(), 0);

    assertThat(page.recordsTotal()).isZero();
  }

  /**
   * A window before the Órgano's earliest publication matches nothing while the Órgano's whole
   * count stands — which is what makes the count a completeness test rather than a page total.
   */
  @Test
  void carries_records_total_on_page_that_matched_nothing() {
    ContratoMenorSourcePage page = new ContratoMenorSourcePage(List.of(), RECORDS_TOTAL);

    assertThat(page)
        .extracting(ContratoMenorSourcePage::entries, ContratoMenorSourcePage::recordsTotal)
        .containsExactly(List.of(), RECORDS_TOTAL);
  }

  @Test
  void keeps_the_entries_it_was_given_when_the_caller_list_changes() {
    List<ContratoMenorSourceEntry> entries = new ArrayList<>(List.of(entry(2001090L)));
    ContratoMenorSourcePage page = new ContratoMenorSourcePage(entries, RECORDS_TOTAL);

    entries.add(entry(2001110L));

    assertThat(page.entries()).containsExactly(entry(2001090L));
  }

  /**
   * Built from a mutable list on purpose: {@code List.copyOf} hands an already-immutable argument
   * straight back, so passing {@code List.of} here would assert a property of the argument and
   * stay green with the defensive copy deleted.
   */
  @Test
  void hands_out_the_entries_as_an_unmodifiable_list() {
    ContratoMenorSourcePage page =
        new ContratoMenorSourcePage(new ArrayList<>(List.of(entry(2001090L))), RECORDS_TOTAL);

    assertThatThrownBy(() -> page.entries().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static ContratoMenorSourceEntry entry(long sourceId) {
    return new ContratoMenorSourceEntry(
        sourceId,
        LocalDate.of(2026, 5, 5),
        "Servizos técnicos de electricidade",
        new Money(new BigDecimal("3630.00")),
        "1 mes",
        "Angel Cabarcos Abadin",
        "33545498K");
  }
}
