package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

/**
 * What a page means is {@link LicitacionRecordDocumentTest}'s; this covers the request the adapter
 * issues and its judgement of whether an answer arrived at all.
 */
@ExtendWith(MockitoExtension.class)
class ContratosDeGaliciaLicitacionRecordSourceAdapterTest {

  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");

  private static final String RECORD =
      """
      <div class="col-md-6 text-right"><p>Estado do procedemento: <em>Formalizado</em></p></div>
      <div class="row infoConcurso"><dl><dt>Obxecto</dt><dd>Mellora</dd></dl></div>
      """;

  @Mock private LicitacionRecordClient licitacionRecordClient;

  /**
   * Strict stubbing on the identifier is itself the assertion that it is passed through: a stub
   * keyed on {@code "822054"} answers nothing at all if the adapter asks for something else.
   */
  @Test
  void answers_the_record_the_source_publishes_for_the_publication_it_was_given() {
    stubRecord().thenReturn(HttpResponse.ok(RECORD.getBytes(StandardCharsets.ISO_8859_1)));

    LicitacionRecord record = adapter().fetchRecord(PUBLICATION_ID);

    assertThat(record.publicationId()).isEqualTo(PUBLICATION_ID);
    assertThat(record.obxecto()).isEqualTo("Mellora");
  }

  @Test
  void throws_when_the_source_responds_with_an_error_status() {
    stubRecord()
        .thenThrow(new HttpClientResponseException("Server Error", HttpResponse.serverError()));

    assertThatThrownBy(this::fetchRecord)
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasCauseInstanceOf(HttpClientResponseException.class);
  }

  /** The one error status a declarative client returns rather than throws. */
  @Test
  void throws_naming_the_status_when_the_endpoint_is_not_found() {
    stubRecord().thenReturn(HttpResponse.notFound());

    assertThatThrownBy(this::fetchRecord)
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("404");
  }

  @Test
  void throws_when_the_source_is_unreachable() {
    stubRecord().thenThrow(new HttpClientException("Connect Error: Connection refused"));

    assertThatThrownBy(this::fetchRecord)
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasCauseInstanceOf(HttpClientException.class);
  }

  @Test
  void throws_when_the_source_responds_with_an_empty_body() {
    stubRecord().thenReturn(HttpResponse.ok());

    assertThatThrownBy(this::fetchRecord)
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasNoCause();
  }

  /**
   * The failure that never announces itself: an unknown identifier answers a perfectly ordinary
   * 200, so nothing before the parse can tell it from a procedure that is there.
   */
  @Test
  void throws_when_the_source_answers_something_that_is_not_the_record() {
    stubRecord()
        .thenReturn(
            HttpResponse.ok("<html><body>Erro</body></html>".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(this::fetchRecord)
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasNoCause();
  }

  /** A publication we failed to supply is a bug of ours, and never reaches the source's budget. */
  @Test
  void refuses_the_missing_publication_without_issuing_request() {
    assertThatThrownBy(() -> adapter().fetchRecord(null))
        .isInstanceOf(NullPointerException.class);
    verifyNoInteractions(licitacionRecordClient);
  }

  private ContratosDeGaliciaLicitacionRecordSourceAdapter adapter() {
    return new ContratosDeGaliciaLicitacionRecordSourceAdapter(licitacionRecordClient);
  }

  private LicitacionRecord fetchRecord() {
    return adapter().fetchRecord(PUBLICATION_ID);
  }

  private OngoingStubbing<HttpResponse<byte[]>> stubRecord() {
    return when(licitacionRecordClient.record(PUBLICATION_ID.value()));
  }
}
