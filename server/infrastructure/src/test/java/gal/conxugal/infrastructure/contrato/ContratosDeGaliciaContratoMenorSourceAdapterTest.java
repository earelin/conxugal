package gal.conxugal.infrastructure.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
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
 * What one published row means is {@link ContratosMenoresRowTest}'s; this covers the request the
 * adapter issues, the slices it refuses to issue at all, and its judgement of whether an answer is
 * usable.
 */
@ExtendWith(MockitoExtension.class)
class ContratosDeGaliciaContratoMenorSourceAdapterTest {

  private static final int MAX_PAGE_SIZE =
      ContratosDeGaliciaContratoMenorSourceAdapter.MAX_PAGE_SIZE;

  private static final String SOURCE_KEY = "242";

  /** Exactly the three months the source allows, so the accepted boundary is exercised too. */
  private static final LocalDate FROM = LocalDate.of(2026, 5, 5);

  private static final LocalDate TO = LocalDate.of(2026, 8, 5);
  private static final int OFFSET = 0;
  private static final long RECORDS_TOTAL = 14822L;
  private static final long SOURCE_ID = 2001090L;

  @Mock private ContratosMenoresClient contratosMenoresClient;

  /** The published row reaches the port narrowed, so the conversion is really delegated to. */
  @Test
  void returns_the_rows_converted_to_source_entries() {
    stubPage(
        new ContratosMenoresRow(
            SOURCE_ID,
            "05-05-2026",
            "Obxecto",
            new BigDecimal("3630.00"),
            "33545498K           ",
            "ANGEL CABARCOS ABADIN    ",
            "1 mes"));

    ContratoMenorSourcePage page = fetchPage();

    assertThat(page.entries())
        .containsExactly(
            new ContratoMenorSourceEntry(
                SOURCE_ID,
                LocalDate.of(2026, 5, 5),
                "Obxecto",
                new Money(new BigDecimal("3630.00")),
                "1 mes",
                "ANGEL CABARCOS ABADIN",
                "33545498K"));
  }

  @Test
  void surfaces_records_total_on_page_that_matched_nothing() {
    stubPage();

    ContratoMenorSourcePage page = fetchPage();

    assertThat(page.recordsTotal()).isEqualTo(RECORDS_TOTAL);
  }

  @Test
  void refuses_windows_wider_than_three_months_without_issuing_request() {
    assertThatThrownBy(() -> adapter().fetchPage(SOURCE_KEY, FROM, TO.plusDays(1), OFFSET, 100))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(contratosMenoresClient);
  }

  @Test
  void refuses_page_sizes_above_the_source_maximum_without_issuing_request() {
    assertThatThrownBy(
            () -> adapter().fetchPage(SOURCE_KEY, FROM, TO, OFFSET, MAX_PAGE_SIZE + 1))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(contratosMenoresClient);
  }

  @Test
  void refuses_windows_that_end_before_they_start_without_issuing_request() {
    assertThatThrownBy(() -> adapter().fetchPage(SOURCE_KEY, TO, FROM, OFFSET, 100))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(contratosMenoresClient);
  }

  @Test
  void refuses_negative_offsets_without_issuing_request() {
    assertThatThrownBy(() -> adapter().fetchPage(SOURCE_KEY, FROM, TO, -1, 100))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(contratosMenoresClient);
  }

  @Test
  void throws_when_the_source_responds_with_an_error_status() {
    stubTable()
        .thenThrow(new HttpClientResponseException("Server Error", HttpResponse.serverError()));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientResponseException.class);
  }

  /** The one error status a declarative client returns rather than throws. */
  @Test
  void throws_naming_the_status_when_the_endpoint_is_not_found() {
    stubTable().thenReturn(HttpResponse.notFound());

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasMessageContaining("NOT_FOUND");
  }

  @Test
  void throws_when_the_source_is_unreachable() {
    stubTable().thenThrow(new HttpClientException("Connect Error: Connection refused"));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientException.class);
  }

  @Test
  void throws_when_the_source_responds_with_an_empty_body() {
    stubTable().thenReturn(HttpResponse.ok());

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_response_carries_no_records_total() {
    stubTable().thenReturn(HttpResponse.ok(new ContratosMenoresTable(null, List.of())));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasNoCause();
  }

  /**
   * Typed, not an {@link IllegalArgumentException}: the page's own constructor would refuse a
   * negative count that way, and this port reserves that for a slice we asked for wrongly. A count
   * the source published cannot be our mistake, and a caller that retries one but not the other
   * would abandon the import over it.
   */
  @Test
  void throws_when_the_response_carries_negative_records_total() {
    stubTable().thenReturn(HttpResponse.ok(new ContratosMenoresTable(-1L, List.of())));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_response_carries_no_rows_array() {
    stubTable().thenReturn(HttpResponse.ok(new ContratosMenoresTable(RECORDS_TOTAL, null)));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_response_carries_missing_row() {
    stubTable()
        .thenReturn(
            HttpResponse.ok(
                new ContratosMenoresTable(RECORDS_TOTAL, Collections.singletonList(null))));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class)
        .hasNoCause();
  }

  private ContratosDeGaliciaContratoMenorSourceAdapter adapter() {
    return new ContratosDeGaliciaContratoMenorSourceAdapter(contratosMenoresClient);
  }

  private ContratoMenorSourcePage fetchPage() {
    return adapter().fetchPage(SOURCE_KEY, FROM, TO, OFFSET, MAX_PAGE_SIZE);
  }

  /**
   * Stubbed on the exact arguments the adapter is expected to send, so strict stubbing fails the
   * test if the window is not formatted {@code YYYY-MM-DD} or the paging is not passed through.
   */
  private OngoingStubbing<HttpResponse<ContratosMenoresTable>> stubTable() {
    return when(
        contratosMenoresClient.table(
            SOURCE_KEY, "2026-05-05", "2026-08-05", OFFSET, MAX_PAGE_SIZE, 1));
  }

  private void stubPage(ContratosMenoresRow... rows) {
    stubTable()
        .thenReturn(HttpResponse.ok(new ContratosMenoresTable(RECORDS_TOTAL, List.of(rows))));
  }
}
