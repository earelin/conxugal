package gal.conxugal.infrastructure.licitacion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.LicitacionRecordSource;
import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAmount;
import gal.conxugal.domain.licitacion.VatBasis;
import gal.conxugal.domain.money.Money;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

/**
 * The adapter against pages the source really served, byte for byte. The fixtures are captures
 * rather than stubs because the two things most likely to be got wrong here — the charset and the
 * whitespace inside a published value — are invisible to any fixture written by hand: an ASCII stub
 * decodes identically whichever charset is named, and a tidy one has no double space to lose.
 *
 * <p>The context is rebuilt per test because the circuit breaker is a singleton with memory: these
 * tests deliberately provoke failures, and a breaker shared across them would accumulate enough to
 * open, failing later tests for a reason that has nothing to do with what they assert.
 */
@MicronautTest(startApplication = false, transactional = false, rebuildContext = true)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratosDeGaliciaLicitacionRecordSourceAdapterIntegrationTest
    implements TestPropertyProvider {

  private static final String RECORD_PATH = "/licitacion";

  /** A procedure with two lotes, accented throughout, publishing no reference. */
  private static final PublicationId WITH_LOTES = new PublicationId("822054");

  /** One of the minority that publishes a reference. */
  private static final PublicationId WITH_REFERENCE = new PublicationId("828959");

  /** Not a licitación at all — the same endpoint serves both families from one identifier space. */
  private static final PublicationId CONTRATO_MENOR = new PublicationId("2001090");

  private static final byte[] RECORD_WITH_LOTES = capture("822054.html");
  private static final byte[] RECORD_WITH_REFERENCE = capture("828959.html");
  private static final byte[] RECORD_CONTRATO_MENOR = capture("2001090-contrato-menor.html");

  /** What the source really answers for an identifier it does not know — with a status of 200. */
  private static final byte[] NOT_A_RECORD = capture("not-a-record.html");

  /**
   * Pinned rather than inherited: the retry test counts the requests one fetch makes, so the
   * production default moving must not quietly change what that count means.
   */
  private static final int RETRY_MAX_ATTEMPTS = 3;

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private WireMock source;

  @Inject LicitacionRecordSource licitacionRecordSource;

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!wireMock.isRunning()) {
      wireMock.start();
    }
    return Map.of(
        "micronaut.http.services.contratosdegalicia.url", wireMock.getBaseUrl(),
        "micronaut.http.services.contratosdegalicia.connect-timeout", "5s",
        "micronaut.http.services.contratosdegalicia.read-timeout", "10s",
        "conxugal.contratosdegalicia.resilience.rate-limit-refresh-period", "10ms",
        "conxugal.contratosdegalicia.resilience.retry-base-delay", "10ms",
        "conxugal.contratosdegalicia.resilience.retry-max-attempts",
            String.valueOf(RETRY_MAX_ATTEMPTS));
  }

  @BeforeEach
  void resetSource() {
    source = new WireMock(wireMock.getHost(), wireMock.getPort());
    source.resetMappings();
    source.resetRequests();
    source.resetScenarios();
  }

  /**
   * The charset asserted rather than assumed. The captured bytes are ISO-8859-1 and are not valid
   * UTF-8, so reading them as UTF-8 does not merely mangle the accents — it replaces them, which
   * the last two assertions show. That matters more than it looks: the labels this parse looks
   * values up by are accented too, so a UTF-8 decode would take the record's fields with it.
   */
  @Test
  void reads_the_captured_record_with_every_accent_the_source_published() {
    stubLotesRecord();

    LicitacionRecord record = fetchRecord(WITH_LOTES);

    assertThat(record.obxecto())
        .isNotNull()
        .startsWith("Actuacións de mellora de infraestruturas de eficiencia enerxética");
    assertThat(record.tramitacionType()).isEqualTo("Ordinaria");
    assertThat(new String(RECORD_WITH_LOTES, StandardCharsets.ISO_8859_1))
        .contains("Actuacións");
    assertThat(new String(RECORD_WITH_LOTES, StandardCharsets.UTF_8))
        .doesNotContain("Actuacións")
        .contains("�");
  }

  @Test
  void reads_every_labelled_scalar_the_captured_record_publishes() {
    stubLotesRecord();

    LicitacionRecord record = fetchRecord(WITH_LOTES);

    assertThat(record)
        .extracting(
            LicitacionRecord::contractType,
            LicitacionRecord::procedureType,
            LicitacionRecord::tramitacionType,
            LicitacionRecord::loteCount,
            LicitacionRecord::stateLabel)
        .containsExactly("Obras", "Abertos", "Ordinaria", 2, "Formalizado");
  }

  /**
   * Criterion 44 against bytes the source really sent. This object carries a genuine double space,
   * which is the whole reason the parse reads whole text rather than jsoup's tidied form — and
   * which no fixture written by hand would have contained.
   */
  @Test
  void keeps_the_double_space_the_captured_object_carries() {
    stubLotesRecord();

    LicitacionRecord record = fetchRecord(WITH_LOTES);

    assertThat(record.obxecto()).isNotNull().contains("dentro do  programa");
  }

  /** Galician-formatted text in the record where the listing publishes JSON numbers. */
  @Test
  void reads_the_captured_amounts_at_the_scale_the_source_published() {
    stubLotesRecord();

    LicitacionRecord record = fetchRecord(WITH_LOTES);

    assertThat(record.baseBudget())
        .isEqualTo(
            new PublishedAmount(new Money(new BigDecimal("3378552.09")), VatBasis.INCLUSIVE));
    assertThat(record.estimatedValue())
        .isEqualTo(
            new PublishedAmount(new Money(new BigDecimal("2792191.81")), VatBasis.EXCLUSIVE));
  }

  /** Published by a minority of records, and its absence is an ordinary answer. */
  @Test
  void reads_the_reference_only_from_the_captured_record_that_publishes_one() {
    stubRecord(WITH_REFERENCE, recordResponse(RECORD_WITH_REFERENCE));
    stubLotesRecord();

    assertThat(fetchRecord(WITH_REFERENCE).expediente()).isEqualTo("ATG-2026-0098");
    assertThat(fetchRecord(WITH_LOTES).expediente()).isNull();
  }

  /**
   * The identifier space is shared with contratos menores, so a walk really can be handed a page
   * carrying neither {@code Nº lotes} nor {@code Valor estimado}. That is a record published
   * thinly, not a failure, and refusing it would send a real publication to the ledger for ever.
   */
  @Test
  void answers_the_captured_contrato_menor_with_the_labels_it_omits_absent() {
    stubRecord(CONTRATO_MENOR, recordResponse(RECORD_CONTRATO_MENOR));

    LicitacionRecord record = fetchRecord(CONTRATO_MENOR);

    assertThat(record)
        .extracting(
            LicitacionRecord::loteCount,
            LicitacionRecord::estimatedValue,
            LicitacionRecord::expediente)
        .containsOnlyNulls();
    assertThat(record)
        .extracting(
            LicitacionRecord::contractType,
            LicitacionRecord::procedureType,
            LicitacionRecord::stateLabel)
        .containsExactly("Servizos", "Contrato menor", "Adxudicado");
    assertThat(record.baseBudget())
        .isEqualTo(new PublishedAmount(new Money(new BigDecimal("3630.00")), VatBasis.INCLUSIVE));
    assertThat(record.obxecto()).isNotNull();
  }

  @Test
  void asks_the_source_for_the_record_under_the_parameter_it_publishes() {
    stubLotesRecord();

    fetchRecord(WITH_LOTES);

    assertThat(
            source.find(
                newRequestPattern(RequestMethod.GET, urlPathEqualTo(RECORD_PATH))
                    .withQueryParam("N", equalTo("822054"))))
        .hasSize(1);
  }

  /**
   * The measured not-found path, and the reason no status check can stand in for it: the source
   * answers an unknown identifier with its error page under a perfectly ordinary 200.
   */
  @Test
  void throws_when_the_source_answers_its_error_page_rather_than_the_record() {
    stubRecord(WITH_LOTES, recordResponse(NOT_A_RECORD));

    assertThatThrownBy(() -> fetchRecord(WITH_LOTES))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void retries_transient_source_failure_and_returns_the_eventual_record() {
    source.register(
        WireMock.get(urlPathEqualTo(RECORD_PATH))
            .inScenario("transient")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("failed-once")
            .willReturn(aResponse().withStatus(503)));
    source.register(
        WireMock.get(urlPathEqualTo(RECORD_PATH))
            .inScenario("transient")
            .whenScenarioStateIs("failed-once")
            .willReturn(recordResponse(RECORD_WITH_LOTES)));

    LicitacionRecord record = fetchRecord(WITH_LOTES);

    assertThat(record.stateLabel()).isEqualTo("Formalizado");
    assertThat(recordRequests()).hasSize(2);
  }

  @Test
  void throws_when_the_stubbed_source_responds_with_an_error_status() {
    stubRecord(WITH_LOTES, aResponse().withStatus(500));

    assertThatThrownBy(() -> fetchRecord(WITH_LOTES))
        .isInstanceOf(LicitacionRecordUnavailableException.class);
  }

  @Test
  void throws_naming_the_status_when_the_stubbed_record_is_not_found() {
    stubRecord(WITH_LOTES, aResponse().withStatus(404));

    assertThatThrownBy(() -> fetchRecord(WITH_LOTES))
        .isInstanceOf(LicitacionRecordUnavailableException.class)
        .hasMessageContaining("404");
  }

  @Test
  void throws_when_the_stubbed_source_connection_is_reset() {
    stubRecord(WITH_LOTES, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    assertThatThrownBy(() -> fetchRecord(WITH_LOTES))
        .isInstanceOf(LicitacionRecordUnavailableException.class);
  }

  /** The capture most of these tests read, stubbed under the identifier it publishes. */
  private void stubLotesRecord() {
    stubRecord(WITH_LOTES, recordResponse(RECORD_WITH_LOTES));
  }

  private LicitacionRecord fetchRecord(PublicationId publicationId) {
    return licitacionRecordSource.fetchRecord(publicationId);
  }

  private void stubRecord(PublicationId publicationId, ResponseDefinitionBuilder response) {
    source.register(
        WireMock.get(urlPathEqualTo(RECORD_PATH))
            .withQueryParam("N", equalTo(publicationId.value()))
            .willReturn(response));
  }

  private List<LoggedRequest> recordRequests() {
    return source.find(newRequestPattern(RequestMethod.GET, urlPathEqualTo(RECORD_PATH)));
  }

  /** The captured bytes go out untouched — a round trip through a String would decode them. */
  private static ResponseDefinitionBuilder recordResponse(byte[] page) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/html;charset=ISO-8859-1")
        .withBody(page);
  }

  private static byte[] capture(String name) {
    try (InputStream page =
        ContratosDeGaliciaLicitacionRecordSourceAdapterIntegrationTest.class.getResourceAsStream(
            "/licitacion/" + name)) {
      if (page == null) {
        throw new IllegalStateException(
            "Captured record %s is not on the classpath".formatted(name));
      }
      return page.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
