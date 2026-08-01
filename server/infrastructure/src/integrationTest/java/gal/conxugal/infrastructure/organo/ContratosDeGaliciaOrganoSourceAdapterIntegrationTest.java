package gal.conxugal.infrastructure.organo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gal.conxugal.domain.organo.OrganoSource;
import gal.conxugal.domain.organo.OrganoSourceEntry;
import gal.conxugal.domain.organo.OrganoSourceUnavailableException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
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
 * The context is rebuilt per test because the circuit breaker is a singleton with memory: these
 * tests deliberately provoke failures, and a breaker shared across them would accumulate enough to
 * open, failing later tests for a reason that has nothing to do with what they assert.
 */
@MicronautTest(startApplication = false, transactional = false, rebuildContext = true)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratosDeGaliciaOrganoSourceAdapterIntegrationTest implements TestPropertyProvider {

  private static final int MIN_EXPECTED_ORGANOS =
      ContratosDeGaliciaOrganoSourceAdapter.MIN_EXPECTED_ORGANOS;
  private static final String PORTADA_PATH = PortadaClient.PORTADA_PATH;

  /**
   * Pinned rather than inherited: the retry test counts the requests one fetch makes, so the
   * production default moving must not quietly change what that count means.
   */
  private static final int RETRY_MAX_ATTEMPTS = 3;

  private static final String PLACEHOLDER =
      option("", "Seleccione o organismo que desexa consultar");

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private WireMock source;

  @Inject OrganoSource organoSource;

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

  @Test
  void returns_the_organos_list_decoded_from_the_stubbed_source() {
    String accentedName = "Consello Galego de Relacións Laborais e Función Pública";
    stubPortada(portadaResponse(portadaListing(option("512", accentedName))));

    List<OrganoSourceEntry> entries = organoSource.fetchAll();

    assertThat(entries).contains(new OrganoSourceEntry("512", accentedName));
  }

  @Test
  void retries_transient_source_failure_and_returns_the_eventual_list() {
    String html = portadaListing(option("512", "Consorcio Galego"));
    source.register(
        WireMock.get(urlEqualTo(PORTADA_PATH))
            .inScenario("transient")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("failed-once")
            .willReturn(aResponse().withStatus(503)));
    source.register(
        WireMock.get(urlEqualTo(PORTADA_PATH))
            .inScenario("transient")
            .whenScenarioStateIs("failed-once")
            .willReturn(portadaResponse(html)));

    List<OrganoSourceEntry> entries = organoSource.fetchAll();

    assertThat(entries).contains(new OrganoSourceEntry("512", "Consorcio Galego"));
    assertThat(portadaRequests()).hasSize(2);
  }

  @Test
  void throws_when_the_stubbed_source_responds_with_an_error_status() {
    stubPortada(aResponse().withStatus(500));

    assertThatThrownBy(organoSource::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientResponseException.class);
  }

  /** A 404 is handed back as a response, not thrown, so only the adapter can fail the run on it. */
  @Test
  void throws_naming_the_status_when_the_stubbed_portada_is_not_found() {
    stubPortada(aResponse().withStatus(404));

    assertThatThrownBy(organoSource::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasMessageContaining("NOT_FOUND");
  }

  @Test
  void throws_when_the_stubbed_source_connection_is_reset() {
    stubPortada(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    assertThatThrownBy(organoSource::fetchAll).isInstanceOf(OrganoSourceUnavailableException.class);
  }

  @Test
  void throws_when_the_stubbed_page_has_no_organos_list() {
    stubPortada(portadaResponse("<html><body><p>unexpected page</p></body></html>"));

    assertThatThrownBy(organoSource::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_stubbed_organos_list_is_implausibly_small() {
    String html = portadaHtml(PLACEHOLDER + option("48", "Academia Galega"));
    stubPortada(portadaResponse(html));

    assertThatThrownBy(organoSource::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  private void stubPortada(ResponseDefinitionBuilder response) {
    source.register(WireMock.get(urlEqualTo(PORTADA_PATH)).willReturn(response));
  }

  private List<LoggedRequest> portadaRequests() {
    return source.find(newRequestPattern(RequestMethod.GET, urlEqualTo(PORTADA_PATH)));
  }

  private static ResponseDefinitionBuilder portadaResponse(String html) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/html;charset=ISO-8859-1")
        .withBody(html.getBytes(StandardCharsets.ISO_8859_1));
  }

  /** A listing long enough to clear the adapter's implausibly-small floor. */
  private static String portadaListing(String optionHtml) {
    return portadaHtml(PLACEHOLDER + optionHtml + paddingOptions(MIN_EXPECTED_ORGANOS));
  }

  private static String portadaHtml(String optionsHtml) {
    return "<html><body><select id=\"organoA\">%s</select></body></html>".formatted(optionsHtml);
  }

  private static String option(String value, String text) {
    return "<option value='%s'>%s</option>".formatted(value, text);
  }

  private static String paddingOptions(int count) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      builder.append(option("pad-%d".formatted(i), "Padding Body %d".formatted(i)));
    }
    return builder.toString();
  }
}
