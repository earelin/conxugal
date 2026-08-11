package gal.conxugal.infrastructure.contrato;

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
import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import gal.conxugal.domain.money.Money;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

/**
 * The context is rebuilt per test because the circuit breaker is a singleton with memory: these
 * tests deliberately provoke failures, and a breaker shared across them would accumulate enough to
 * open, failing later tests for a reason that has nothing to do with what they assert.
 */
@MicronautTest(startApplication = false, transactional = false, rebuildContext = true)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratosDeGaliciaContratoMenorSourceAdapterIntegrationTest implements TestPropertyProvider {

  private static final String SOURCE_KEY = "242";
  private static final String TABLE_PATH = "/api/v1/organismos/242/contratosmenores/table";

  private static final LocalDate FROM = LocalDate.of(2026, 5, 5);
  private static final LocalDate TO = LocalDate.of(2026, 8, 5);
  private static final int PAGE_SIZE = 100;
  private static final long RECORDS_TOTAL = 14822L;

  /**
   * Pinned rather than inherited: the retry test counts the requests one fetch makes, so the
   * production default moving must not quietly change what that count means.
   */
  private static final int RETRY_MAX_ATTEMPTS = 3;

  /** 91 characters, so a 60-character cap anywhere in the binding would show as a short string. */
  private static final String SAMPLE_OBJETO =
      "SERVIZOS TÉCNICOS DE ELECTRICIDADE PARA A CELEBRACIÓN DA FESTA DO ALBARIÑO DE CAMBADOS 2026";

  /**
   * The sample row of the measured source contract, padding and all — except that its object runs
   * past the 60 characters the contract once mistook for a cap, so every test using this row
   * exercises the response binding at a length a reintroduced cap would cut.
   */
  private static final String SAMPLE_ROW =
      """
      {"id":2001090,"publicado":"05-05-2026",\
      "objeto":"%s",\
      "importe":3630.00,"nif":"33545498K           ",\
      "adjudicatario":"ANGEL CABARCOS ABADIN                             ",\
      "duracion":"1 mes"}\
      """
          .formatted(SAMPLE_OBJETO);

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private WireMock source;

  @Inject ContratoMenorSource contratoMenorSource;

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
   * Accented text decoded from UTF-8 without mojibake, the padding gone from the fixed-width
   * fields, and the amount at the scale the source published — {@link Money} equality is
   * scale-sensitive, so binding through a floating-point number would fail here.
   */
  @Test
  void returns_the_page_decoded_from_the_stubbed_source() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    ContratoMenorSourcePage page = fetchPage();

    assertThat(page.entries())
        .containsExactly(
            new ContratoMenorSourceEntry(
                2001090L,
                LocalDate.of(2026, 5, 5),
                SAMPLE_OBJETO,
                new Money(new BigDecimal("3630.00")),
                "1 mes",
                "ANGEL CABARCOS ABADIN",
                "33545498K"));
  }

  @Test
  void issues_the_window_and_paging_the_source_contract_documents() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    fetchPage();

    assertThat(
            source.find(
                newRequestPattern(RequestMethod.GET, urlPathEqualTo(TABLE_PATH))
                    .withQueryParam("datestart", equalTo("2026-05-05"))
                    .withQueryParam("dateend", equalTo("2026-08-05"))
                    .withQueryParam("start", equalTo("0"))
                    .withQueryParam("length", equalTo("100"))
                    .withQueryParam("draw", equalTo("1"))))
        .hasSize(1);
  }

  @Test
  void retries_transient_source_failure_and_returns_the_eventual_page() {
    source.register(
        WireMock.get(urlPathEqualTo(TABLE_PATH))
            .inScenario("transient")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("failed-once")
            .willReturn(aResponse().withStatus(503)));
    source.register(
        WireMock.get(urlPathEqualTo(TABLE_PATH))
            .inScenario("transient")
            .whenScenarioStateIs("failed-once")
            .willReturn(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW))));

    ContratoMenorSourcePage page = fetchPage();

    assertThat(page.entries()).hasSize(1);
    assertThat(tableRequests()).hasSize(2);
  }

  @Test
  void returns_records_total_when_the_stubbed_window_matched_nothing() {
    stubTable(tableResponse(table(RECORDS_TOTAL)));

    ContratoMenorSourcePage page = fetchPage();

    assertThat(page.recordsTotal()).isEqualTo(RECORDS_TOTAL);
  }

  @Test
  void throws_when_the_stubbed_source_responds_with_an_error_status() {
    stubTable(aResponse().withStatus(500));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class);
  }

  @Test
  void throws_when_the_stubbed_source_connection_is_reset() {
    stubTable(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class);
  }

  @Test
  void throws_when_the_stubbed_body_cannot_be_read() {
    stubTable(tableResponse("<html>a maintenance page, not the table</html>"));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(ContratoMenorSourceUnavailableException.class);
  }

  /**
   * The one failure that must never reach the source: an over-wide window answers a bare 500 with
   * no machine-readable body, so a bug of ours would otherwise be counted against the breaker.
   */
  @Test
  void refuses_over_wide_window_without_reaching_the_stubbed_source() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    assertThatThrownBy(
            () -> contratoMenorSource.fetchPage(SOURCE_KEY, FROM, TO.plusDays(1), 0, PAGE_SIZE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(tableRequests()).isEmpty();
  }

  @Test
  void refuses_over_large_page_without_reaching_the_stubbed_source() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    assertThatThrownBy(
            () -> contratoMenorSource.fetchPage(SOURCE_KEY, FROM, TO, 0, PAGE_SIZE + 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(tableRequests()).isEmpty();
  }

  private ContratoMenorSourcePage fetchPage() {
    return contratoMenorSource.fetchPage(SOURCE_KEY, FROM, TO, 0, PAGE_SIZE);
  }

  private void stubTable(ResponseDefinitionBuilder response) {
    source.register(WireMock.get(urlPathEqualTo(TABLE_PATH)).willReturn(response));
  }

  private List<LoggedRequest> tableRequests() {
    return source.find(newRequestPattern(RequestMethod.GET, urlPathEqualTo(TABLE_PATH)));
  }

  private static ResponseDefinitionBuilder tableResponse(String json) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json;charset=UTF-8")
        .withBody(json.getBytes(StandardCharsets.UTF_8));
  }

  private static String table(long recordsTotal, String... rows) {
    return
        """
        {"draw":1,"recordsTotal":%d,"recordsFiltered":%d,"data":[%s]}\
        """.formatted(recordsTotal, rows.length, String.join(",", rows));
  }
}
