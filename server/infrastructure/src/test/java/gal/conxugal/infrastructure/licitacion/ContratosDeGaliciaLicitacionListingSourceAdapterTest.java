package gal.conxugal.infrastructure.licitacion;

import static gal.conxugal.domain.licitacion.LicitacionListingOrder.ID_ASCENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.licitacion.LicitacionListingEntry;
import gal.conxugal.domain.licitacion.LicitacionListingPage;
import gal.conxugal.domain.licitacion.LicitacionListingUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.money.Money;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

/**
 * What one published row means is {@link LicitacionsRowTest}'s; this covers the request the
 * adapter issues, the pages it refuses to issue at all, and its judgement of whether an answer is
 * usable.
 */
@ExtendWith(MockitoExtension.class)
class ContratosDeGaliciaLicitacionListingSourceAdapterTest {

  private static final int MAX_PAGE_SIZE =
      ContratosDeGaliciaLicitacionListingSourceAdapter.MAX_PAGE_SIZE;

  private static final String SOURCE_KEY = "242";
  private static final int OFFSET = 0;
  private static final long RECORDS_TOTAL = 16798L;
  private static final long PUBLICATION_ID = 822054L;

  @Mock private LicitacionsClient licitacionsClient;

  /** The published row reaches the port narrowed, so the conversion is really delegated to. */
  @Test
  void returns_the_rows_converted_to_listing_entries() {
    stubPage(row(PUBLICATION_ID));

    LicitacionListingPage page = fetchPage();

    assertThat(page.entries())
        .containsExactly(
            new LicitacionListingEntry(
                new PublicationId("822054"),
                LocalDate.of(2024, 3, 8),
                LocalDate.of(2026, 8, 20),
                "Obxecto",
                new Money(new BigDecimal("3378552.09")),
                8,
                "Formalizado"));
  }

  /**
   * The source's own order is the answer, and the adapter adds none of its own — a walk resuming
   * from an offset depends on the page it reads matching the page the source paged.
   */
  @Test
  void returns_the_rows_in_the_order_the_source_sent_them() {
    stubPage(row(828959L), row(PUBLICATION_ID), row(18747L));

    LicitacionListingPage page = fetchPage();

    assertThat(page.entries())
        .extracting(entry -> entry.publicationId().value())
        .containsExactly("828959", "822054", "18747");
  }

  @Test
  void surfaces_records_total_on_page_that_held_nothing() {
    stubPage();

    LicitacionListingPage page = fetchPage();

    assertThat(page.recordsTotal()).isEqualTo(RECORDS_TOTAL);
  }

  @Test
  void refuses_page_sizes_above_the_source_maximum_without_issuing_request() {
    assertThatThrownBy(
            () -> adapter().fetchPage(SOURCE_KEY, ID_ASCENDING, OFFSET, MAX_PAGE_SIZE + 1))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(licitacionsClient);
  }

  @Test
  void refuses_empty_page_sizes_without_issuing_request() {
    assertThatThrownBy(() -> adapter().fetchPage(SOURCE_KEY, ID_ASCENDING, OFFSET, 0))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(licitacionsClient);
  }

  @Test
  void refuses_negative_offsets_without_issuing_request() {
    assertThatThrownBy(() -> adapter().fetchPage(SOURCE_KEY, ID_ASCENDING, -1, MAX_PAGE_SIZE))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(licitacionsClient);
  }

  @Test
  void refuses_blank_source_keys_without_issuing_request() {
    assertThatThrownBy(() -> adapter().fetchPage("  ", ID_ASCENDING, OFFSET, MAX_PAGE_SIZE))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(licitacionsClient);
  }

  @Test
  void throws_when_the_source_responds_with_an_error_status() {
    stubTable()
        .thenThrow(new HttpClientResponseException("Server Error", HttpResponse.serverError()));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasCauseInstanceOf(HttpClientResponseException.class);
  }

  /** The one error status a declarative client returns rather than throws. */
  @Test
  void throws_naming_the_status_when_the_endpoint_is_not_found() {
    stubTable().thenReturn(HttpResponse.notFound());

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasMessageContaining("NOT_FOUND");
  }

  @Test
  void throws_when_the_source_is_unreachable() {
    stubTable().thenThrow(new HttpClientException("Connect Error: Connection refused"));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasCauseInstanceOf(HttpClientException.class);
  }

  @Test
  void throws_when_the_source_responds_with_an_empty_body() {
    stubTable().thenReturn(HttpResponse.ok());

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_response_carries_no_records_total() {
    stubTable().thenReturn(HttpResponse.ok(new LicitacionsTable(null, List.of())));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasNoCause();
  }

  /**
   * Typed, not an {@link IllegalArgumentException}: the page's own constructor would refuse a
   * negative count that way, and this port reserves that for a page we asked for wrongly. A count
   * the source published cannot be our mistake, and a caller that retries one but not the other
   * would abandon the import over it.
   */
  @Test
  void throws_when_the_response_carries_negative_records_total() {
    stubTable().thenReturn(HttpResponse.ok(new LicitacionsTable(-1L, List.of())));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_response_carries_no_rows_array() {
    stubTable().thenReturn(HttpResponse.ok(new LicitacionsTable(RECORDS_TOTAL, null)));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_response_carries_missing_row() {
    stubTable()
        .thenReturn(
            HttpResponse.ok(
                new LicitacionsTable(RECORDS_TOTAL, Collections.singletonList(null))));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasNoCause();
  }

  private ContratosDeGaliciaLicitacionListingSourceAdapter adapter() {
    return new ContratosDeGaliciaLicitacionListingSourceAdapter(licitacionsClient);
  }

  private LicitacionListingPage fetchPage() {
    return adapter().fetchPage(SOURCE_KEY, ID_ASCENDING, OFFSET, MAX_PAGE_SIZE);
  }

  /**
   * Stubbed on the exact arguments the adapter is expected to send, so strict stubbing fails the
   * test if the paging is not passed through or the order does not resolve to the {@code id}
   * column ascending.
   */
  private OngoingStubbing<HttpResponse<LicitacionsTable>> stubTable() {
    return when(
        licitacionsClient.table(
            SOURCE_KEY, OFFSET, MAX_PAGE_SIZE, 1, LicitacionsClient.ID_COLUMN, "asc"));
  }

  private void stubPage(LicitacionsRow... rows) {
    stubTable().thenReturn(HttpResponse.ok(new LicitacionsTable(RECORDS_TOTAL, List.of(rows))));
  }

  private static LicitacionsRow row(long publicationId) {
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
