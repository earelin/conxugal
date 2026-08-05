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

@ExtendWith(MockitoExtension.class)
class ContratosDeGaliciaContratoMenorSourceAdapterTest {

  private static final int MAX_DURATION_LENGTH =
      ContratosDeGaliciaContratoMenorSourceAdapter.MAX_DURATION_LENGTH;
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

  @Test
  void strips_the_padding_from_the_fixed_width_fields() {
    stubPage(rowAwardedTo("33545498K           ", "  Talleres  López, S.L.   "));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry)
        .extracting(
            ContratoMenorSourceEntry::awardeeFiscalId, ContratoMenorSourceEntry::awardeeName)
        .containsExactly("33545498K", "Talleres  López, S.L.");
  }

  @Test
  void carries_text_that_is_empty_once_stripped_as_absent() {
    stubPage(rowAwardedTo("", "   "));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry)
        .extracting(
            ContratoMenorSourceEntry::awardeeFiscalId, ContratoMenorSourceEntry::awardeeName)
        .containsOnlyNulls();
  }

  /**
   * The source contract once recorded a 60-character cap on the object that it does not have.
   * Nothing here may reintroduce one.
   */
  @Test
  void carries_the_object_at_its_full_published_length() {
    String objeto = "SERVIZOS TÉCNICOS DE ELECTRICIDADE ".repeat(20).strip();
    stubPage(rowDescribing(objeto));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry.obxecto()).isEqualTo(objeto);
  }

  @Test
  void caps_the_duration_at_the_maximum_when_it_is_longer() {
    String duracion = "z".repeat(MAX_DURATION_LENGTH + 1);
    stubPage(rowLasting(duracion));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry.duration()).isEqualTo("z".repeat(MAX_DURATION_LENGTH));
  }

  @Test
  void leaves_the_duration_untouched_at_exactly_the_maximum() {
    String duracion = "z".repeat(MAX_DURATION_LENGTH);
    stubPage(rowLasting(duracion));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry.duration()).isEqualTo(duracion);
  }

  /** {@link Money} equality is scale-sensitive, so this fails if anything rescales the figure. */
  @Test
  void carries_the_amount_at_the_scale_the_source_published() {
    stubPage(rowCosting(new BigDecimal("3630.00")));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry.amount()).isEqualTo(new Money(new BigDecimal("3630.00")));
  }

  @Test
  void carries_an_absent_amount_as_absent() {
    stubPage(rowCosting(null));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry.amount()).isNull();
  }

  @Test
  void interprets_the_publication_date_from_its_published_text() {
    stubPage(rowPublishedOn("05-05-2026"));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry.publicationDate()).isEqualTo(LocalDate.of(2026, 5, 5));
  }

  /** A date nobody can read is not a reason to refuse an award the source really published. */
  @Test
  void carries_an_uninterpretable_publication_date_as_absent() {
    stubPage(rowPublishedOn("dunha vez"));

    ContratoMenorSourceEntry entry = onlyEntry();

    assertThat(entry.publicationDate()).isNull();
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

  @Test
  void throws_when_row_carries_no_identifier() {
    stubPage(row(null, "05-05-2026", "Obxecto", BigDecimal.ONE, "33545498K", "Nome", "1 mes"));

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

  private ContratoMenorSourceEntry onlyEntry() {
    List<ContratoMenorSourceEntry> entries = fetchPage().entries();
    assertThat(entries).hasSize(1);
    return entries.getFirst();
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

  private static ContratosMenoresRow rowAwardedTo(String nif, String adjudicatario) {
    return row(SOURCE_ID, "05-05-2026", "Obxecto", BigDecimal.ONE, nif, adjudicatario, "1 mes");
  }

  private static ContratosMenoresRow rowDescribing(String objeto) {
    return row(SOURCE_ID, "05-05-2026", objeto, BigDecimal.ONE, "33545498K", "Nome", "1 mes");
  }

  private static ContratosMenoresRow rowLasting(String duracion) {
    return row(SOURCE_ID, "05-05-2026", "Obxecto", BigDecimal.ONE, "33545498K", "Nome", duracion);
  }

  private static ContratosMenoresRow rowCosting(BigDecimal importe) {
    return row(SOURCE_ID, "05-05-2026", "Obxecto", importe, "33545498K", "Nome", "1 mes");
  }

  private static ContratosMenoresRow rowPublishedOn(String publicado) {
    return row(SOURCE_ID, publicado, "Obxecto", BigDecimal.ONE, "33545498K", "Nome", "1 mes");
  }

  private static ContratosMenoresRow row(
      Long id,
      String publicado,
      String objeto,
      BigDecimal importe,
      String nif,
      String adjudicatario,
      String duracion) {
    return new ContratosMenoresRow(id, publicado, objeto, importe, nif, adjudicatario, duracion);
  }
}
