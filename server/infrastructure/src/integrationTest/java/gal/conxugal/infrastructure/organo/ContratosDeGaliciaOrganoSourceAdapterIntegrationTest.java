package gal.conxugal.infrastructure.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.organo.OrganoSource;
import gal.conxugal.domain.organo.OrganoSourceEntry;
import gal.conxugal.domain.organo.OrganoSourceUnavailableException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratosDeGaliciaOrganoSourceAdapterIntegrationTest implements TestPropertyProvider {

  private static final int MIN_EXPECTED_ORGANOS =
      ContratosDeGaliciaOrganoSourceAdapter.MIN_EXPECTED_ORGANOS;
  private static final String PORTADA_PATH = ContratosDeGaliciaOrganoSourceAdapter.PORTADA_PATH;
  private static final String PLACEHOLDER =
      option("", "Seleccione o organismo que desexa consultar");

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private final HttpClient adminClient = HttpClient.newHttpClient();

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!wireMock.isRunning()) {
      wireMock.start();
    }
    return Map.of("conxugal.contratosdegalicia.base-url", wireMock.getBaseUrl());
  }

  @Inject OrganoSource organoSource;

  @BeforeEach
  void resetStubs() throws Exception {
    adminClient.send(
        HttpRequest.newBuilder(URI.create(wireMock.getBaseUrl() + "/__admin/reset"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.discarding());
  }

  @Test
  void returns_the_organos_list_decoded_from_the_stubbed_source() throws Exception {
    String accentedName = "Consello Galego de Relacións Laborais e Función Pública";
    String optionsHtml =
        PLACEHOLDER + option("512", accentedName) + paddingOptions(MIN_EXPECTED_ORGANOS);
    stubPortadaWithBody(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = organoSource.fetchAll();

    assertThat(entries).contains(new OrganoSourceEntry("512", accentedName));
  }

  @Test
  void throws_when_the_stubbed_source_responds_with_an_error_status() throws Exception {
    stubPortadaWithStatus(500);

    assertThatThrownBy(organoSource::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientResponseException.class);
  }

  @Test
  void throws_when_the_stubbed_source_connection_is_reset() throws Exception {
    stubPortadaWithFault("CONNECTION_RESET_BY_PEER");

    assertThatThrownBy(organoSource::fetchAll).isInstanceOf(OrganoSourceUnavailableException.class);
  }

  @Test
  void throws_when_the_stubbed_page_has_no_organos_list() throws Exception {
    stubPortadaWithBody("<html><body><p>unexpected page</p></body></html>");

    assertThatThrownBy(organoSource::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_stubbed_organos_list_is_implausibly_small() throws Exception {
    String optionsHtml = PLACEHOLDER + option("48", "Academia Galega");
    stubPortadaWithBody(portadaHtml(optionsHtml));

    assertThatThrownBy(organoSource::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  private void stubPortadaWithBody(String html) throws Exception {
    byte[] body = html.getBytes(StandardCharsets.ISO_8859_1);
    String base64Body = Base64.getEncoder().encodeToString(body);
    String json =
        """
        { "request": { "method": "GET", "url": "%s" },
          "response": {
            "status": 200,
            "headers": { "Content-Type": "text/html;charset=ISO-8859-1" },
            "base64Body": "%s"
          }
        }
        """
            .formatted(PORTADA_PATH, base64Body);
    registerMapping(json);
  }

  private void stubPortadaWithStatus(int status) throws Exception {
    String json =
        """
        { "request": { "method": "GET", "url": "%s" },
          "response": { "status": %d }
        }
        """
            .formatted(PORTADA_PATH, status);
    registerMapping(json);
  }

  private void stubPortadaWithFault(String fault) throws Exception {
    String json =
        """
        { "request": { "method": "GET", "url": "%s" },
          "response": { "fault": "%s" }
        }
        """
            .formatted(PORTADA_PATH, fault);
    registerMapping(json);
  }

  private void registerMapping(String mappingJson) throws Exception {
    adminClient.send(
        HttpRequest.newBuilder(URI.create(wireMock.getBaseUrl() + "/__admin/mappings"))
            .POST(HttpRequest.BodyPublishers.ofString(mappingJson))
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.discarding());
  }

  private static String portadaHtml(String optionsHtml) {
    return "<html><body><select id=\"organoA\">" + optionsHtml + "</select></body></html>";
  }

  private static String option(String value, String text) {
    return "<option value='" + value + "'>" + text + "</option>";
  }

  private static String paddingOptions(int count) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      builder.append(option("pad-" + i, "Padding Body " + i));
    }
    return builder.toString();
  }
}
