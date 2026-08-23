package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.licitacion.LicitacionListingPage;
import gal.conxugal.domain.licitacion.LicitacionListingUnavailableException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What one published table response means. What one of its rows means is {@link
 * LicitacionsRowTest}'s; this covers the count beside them, the shapes the response is refused for,
 * and which exception each refusal is.
 */
class LicitacionsTableTest {

  private static final long RECORDS_TOTAL = 1065L;

  @Test
  void answers_the_rows_it_carries_as_listing_entries() {
    LicitacionListingPage page =
        new LicitacionsTable(RECORDS_TOTAL, List.of(row("822054"))).toListingPage();

    assertThat(page.entries())
        .extracting(entry -> entry.publicationId().value())
        .containsExactly("822054");
  }

  /**
   * The source's own order is the answer. A walk pages by offset, so a page re-sorted on the way
   * through would not be the page the source paged.
   */
  @Test
  void answers_the_rows_in_the_order_the_response_carried_them() {
    LicitacionsTable table =
        new LicitacionsTable(RECORDS_TOTAL, List.of(row("828959"), row("822054"), row("18747")));

    assertThat(table.toListingPage().entries())
        .extracting(entry -> entry.publicationId().value())
        .containsExactly("828959", "822054", "18747");
  }

  /**
   * The count is the Órgano's whole published history, not this page's size — it is what makes a
   * walk's completeness provable, and it moves while a multi-hour import runs.
   */
  @Test
  void answers_the_count_the_source_published_rather_than_the_page_size() {
    LicitacionsTable table = new LicitacionsTable(RECORDS_TOTAL, List.of(row("822054")));

    assertThat(table.toListingPage().recordsTotal()).isEqualTo(RECORDS_TOTAL);
  }

  /** An Órgano that has published nothing is an ordinary answer, not a failure. */
  @Test
  void answers_an_empty_page_when_the_source_carried_no_rows() {
    LicitacionListingPage page = new LicitacionsTable(0L, List.of()).toListingPage();

    assertThat(page)
        .extracting(LicitacionListingPage::entries, LicitacionListingPage::recordsTotal)
        .containsExactly(List.of(), 0L);
  }

  /** An offset past the end of the history: no rows, and the count still standing. */
  @Test
  void answers_no_entries_with_the_count_still_carried() {
    LicitacionListingPage page = new LicitacionsTable(RECORDS_TOTAL, List.of()).toListingPage();

    assertThat(page)
        .extracting(LicitacionListingPage::entries, LicitacionListingPage::recordsTotal)
        .containsExactly(List.of(), RECORDS_TOTAL);
  }

  @Test
  void refuses_the_response_when_it_carries_no_rows_array() {
    LicitacionsTable table = new LicitacionsTable(RECORDS_TOTAL, null);

    assertThatThrownBy(table::toListingPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void refuses_the_response_when_one_of_its_rows_is_missing() {
    LicitacionsTable table =
        new LicitacionsTable(RECORDS_TOTAL, Collections.singletonList(null));

    assertThatThrownBy(table::toListingPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasNoCause();
  }

  /**
   * Absent and negative alike: a count the response does not carry, and one it carries as a figure
   * no history can have.
   */
  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {-1L, Long.MIN_VALUE})
  void refuses_the_response_when_the_count_is_missing_or_negative(Long recordsTotal) {
    LicitacionsTable table = new LicitacionsTable(recordsTotal, List.of(row("822054")));

    assertThatThrownBy(table::toListingPage)
        .isInstanceOf(LicitacionListingUnavailableException.class);
  }

  /**
   * The reason the count is range-checked here rather than left to the page's own constructor: that
   * one refuses a negative with an {@link IllegalArgumentException}, which this port answers when
   * <em>we</em> asked for a page wrongly. A count the source published can never be our mistake,
   * and a caller that retries one but not the other would abandon an import over it.
   */
  @Test
  void refuses_the_negative_count_as_the_source_failure_it_is() {
    LicitacionsTable table = new LicitacionsTable(-1L, List.of(row("822054")));

    assertThatThrownBy(table::toListingPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .isNotInstanceOf(IllegalArgumentException.class);
  }

  /** A row the source published unusably fails the response, not just itself. */
  @Test
  void lets_an_unusable_row_refuse_the_whole_response() {
    LicitacionsTable table =
        new LicitacionsTable(
            RECORDS_TOTAL,
            List.of(row("822054"), new LicitacionsRow(null, null, null, null, null, 8, null)));

    assertThatThrownBy(table::toListingPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasMessageContaining("id");
  }

  private static LicitacionsRow row(String publicationId) {
    return new LicitacionsRow(
        publicationId,
        "08-03-2024",
        "20-08-2026",
        "Obxecto",
        new BigDecimal("3378552.09"),
        8,
        "Formalizado");
  }
}
