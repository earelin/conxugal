package gal.conxugal.infrastructure.licitacion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static gal.conxugal.domain.licitacion.LicitacionListingOrder.ID_ASCENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gal.conxugal.domain.licitacion.LicitacionListingEntry;
import gal.conxugal.domain.licitacion.LicitacionListingPage;
import gal.conxugal.domain.licitacion.LicitacionListingSource;
import gal.conxugal.domain.licitacion.LicitacionListingUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
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
class ContratosDeGaliciaLicitacionListingSourceAdapterIntegrationTest
    implements TestPropertyProvider {

  private static final String SOURCE_KEY = "242";
  private static final String TABLE_PATH = "/api/v1/organismos/242/licitaciones/table";

  private static final int PAGE_SIZE = 100;
  private static final long RECORDS_TOTAL = 1065L;

  /**
   * Pinned rather than inherited: the retry test counts the requests one fetch makes, so the
   * production default moving must not quietly change what that count means.
   */
  private static final int RETRY_MAX_ATTEMPTS = 3;

  /** Accented throughout, so decoding the UTF-8 body as anything else shows as mojibake. */
  private static final String SAMPLE_OBJETO =
      "Actuacións de mellora de infraestruturas de eficiencia enerxética nos centros de saúde";

  /** The sample row of the measured source contract. */
  private static final String SAMPLE_ROW =
      """
      {"id":822054,"objeto":"%s",\
      "importe":3378552.09,"estado":8,"estadoDesc":"Formalizado",\
      "publicado":"08-03-2024","modificado":"20-08-2026"}\
      """
          .formatted(SAMPLE_OBJETO);

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private WireMock source;

  @Inject LicitacionListingSource licitacionListingSource;

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
   * Accented text decoded from UTF-8 without mojibake, and the amount at the scale the source
   * published — {@link Money} equality is scale-sensitive, so binding through a floating-point
   * number would fail here.
   */
  @Test
  void returns_the_page_decoded_from_the_stubbed_source() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    LicitacionListingPage page = fetchPage();

    assertThat(page.entries())
        .containsExactly(
            new LicitacionListingEntry(
                new PublicationId("822054"),
                LocalDate.of(2024, 3, 8),
                LocalDate.of(2026, 8, 20),
                SAMPLE_OBJETO,
                new Money(new BigDecimal("3378552.09")),
                8,
                "Formalizado"));
  }

  /**
   * The criterion this task exists for. The source resolves the order column by name, so a request
   * carrying {@code order[0][column]} without the {@code columns[i][name]} array answers 500 —
   * while a lenient stub answers 200 either way. Only asserting the request as the source received
   * it can tell the two apart.
   *
   * <p>The names go out percent-encoded ({@code columns%5B0%5D%5Bname%5D}), which is what the
   * source's own table widget sends and what these matchers see once the query string is decoded.
   */
  @Test
  void issues_the_whole_datatables_payload_the_source_contract_documents() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    fetchPage();

    assertThat(
            source.find(
                newRequestPattern(RequestMethod.GET, urlPathEqualTo(TABLE_PATH))
                    .withQueryParam("columns[0][name]", equalTo("id"))
                    .withQueryParam("columns[1][name]", equalTo("objeto"))
                    .withQueryParam("columns[2][name]", equalTo("importe"))
                    .withQueryParam("columns[3][name]", equalTo("estado"))
                    .withQueryParam("columns[4][name]", equalTo("publicado"))
                    .withQueryParam("columns[5][name]", equalTo("modificado"))
                    .withQueryParam("order[0][column]", equalTo("0"))
                    .withQueryParam("order[0][dir]", equalTo("asc"))
                    .withQueryParam("start", equalTo("0"))
                    .withQueryParam("length", equalTo("100"))
                    .withQueryParam("draw", equalTo("1"))
                    .withQueryParam("idioma", equalTo("gl"))))
        .hasSize(1);
  }

  @Test
  void asks_for_the_offset_the_walk_resumes_from() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    licitacionListingSource.fetchPage(SOURCE_KEY, ID_ASCENDING, 1000, PAGE_SIZE);

    assertThat(
            source.find(
                newRequestPattern(RequestMethod.GET, urlPathEqualTo(TABLE_PATH))
                    .withQueryParam("start", equalTo("1000"))))
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

    LicitacionListingPage page = fetchPage();

    assertThat(page.entries()).hasSize(1);
    assertThat(tableRequests()).hasSize(2);
  }

  /** An Órgano publishing nothing is an ordinary answer, and it still carries its own count. */
  @Test
  void returns_an_empty_page_when_the_stubbed_organo_published_nothing() {
    stubTable(tableResponse(table(0)));

    LicitacionListingPage page = fetchPage();

    assertThat(page)
        .extracting(LicitacionListingPage::entries, LicitacionListingPage::recordsTotal)
        .containsExactly(List.of(), 0L);
  }

  @Test
  void throws_when_the_stubbed_source_responds_with_an_error_status() {
    stubTable(aResponse().withStatus(500));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class);
  }

  @Test
  void throws_when_the_stubbed_source_connection_is_reset() {
    stubTable(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class);
  }

  @Test
  void throws_when_the_stubbed_body_cannot_be_read() {
    stubTable(tableResponse("<html>a maintenance page, not the table</html>"));

    assertThatThrownBy(this::fetchPage)
        .isInstanceOf(LicitacionListingUnavailableException.class);
  }

  /**
   * The one failure that must never reach the source: an over-wide page answers a bare 500 with no
   * machine-readable body, so a bug of ours would otherwise be counted against the breaker.
   */
  @Test
  void refuses_over_large_page_without_reaching_the_stubbed_source() {
    stubTable(tableResponse(table(RECORDS_TOTAL, SAMPLE_ROW)));

    assertThatThrownBy(
            () ->
                licitacionListingSource.fetchPage(
                    SOURCE_KEY, ID_ASCENDING, 0, PAGE_SIZE + 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(tableRequests()).isEmpty();
  }

  private LicitacionListingPage fetchPage() {
    return licitacionListingSource.fetchPage(SOURCE_KEY, ID_ASCENDING, 0, PAGE_SIZE);
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
