package gal.conxugal.domain.licitacion;

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

class LicitacionListingPageTest {

  private static final long RECORDS_TOTAL = 16798L;

  /**
   * The message is asserted because {@code List.copyOf} would throw a bare, unexplained
   * {@link NullPointerException} on its own — so without it this would pass with the explicit
   * check deleted.
   */
  @Test
  void rejects_null_entries() {
    assertThatNullPointerException()
        .isThrownBy(() -> new LicitacionListingPage(null, RECORDS_TOTAL))
        .withMessageContaining("entries");
  }

  @Test
  void rejects_negative_records_total() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LicitacionListingPage(List.of(entry(822054L)), -1));
  }

  /** An Órgano that has published nothing at all is an ordinary answer, not a failure. */
  @Test
  void accepts_records_total_of_zero() {
    LicitacionListingPage page = new LicitacionListingPage(List.of(), 0);

    assertThat(page.recordsTotal()).isZero();
  }

  /**
   * An offset past the end of the history answers no entries while the Órgano's whole count
   * stands — which is what makes the count a completeness test rather than a page total.
   */
  @Test
  void carries_records_total_on_page_that_held_nothing() {
    LicitacionListingPage page = new LicitacionListingPage(List.of(), RECORDS_TOTAL);

    assertThat(page)
        .extracting(LicitacionListingPage::entries, LicitacionListingPage::recordsTotal)
        .containsExactly(List.of(), RECORDS_TOTAL);
  }

  @Test
  void keeps_the_entries_it_was_given_when_the_caller_list_changes() {
    List<LicitacionListingEntry> entries = new ArrayList<>(List.of(entry(822054L)));
    LicitacionListingPage page = new LicitacionListingPage(entries, RECORDS_TOTAL);

    entries.add(entry(828959L));

    assertThat(page.entries()).containsExactly(entry(822054L));
  }

  /**
   * Built from a mutable list on purpose: {@code List.copyOf} hands an already-immutable argument
   * straight back, so passing {@code List.of} here would assert a property of the argument and
   * stay green with the defensive copy deleted.
   */
  @Test
  void hands_out_the_entries_as_an_unmodifiable_list() {
    LicitacionListingPage page =
        new LicitacionListingPage(new ArrayList<>(List.of(entry(822054L))), RECORDS_TOTAL);

    assertThatThrownBy(() -> page.entries().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static LicitacionListingEntry entry(long publicationId) {
    return new LicitacionListingEntry(
        new PublicationId(String.valueOf(publicationId)),
        LocalDate.of(2024, 3, 8),
        LocalDate.of(2026, 8, 20),
        "Actuacións de mellora de infraestruturas de eficiencia enerxética",
        new Money(new BigDecimal("3378552.09")),
        8,
        "Formalizado");
  }
}
